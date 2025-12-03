import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;



public class MainMultiImageJPEG {


    private static final int THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());
    public static final ExecutorService jpegPool = Executors.newFixedThreadPool(128);

    public static final double[][] LUMINANCE_MATRIX = {
            {16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55},
            {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62},
            {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92},
            {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}
    };
    public static final double[][] CHROMINANCE_MATRIX = {
            {17, 18, 24, 47, 99, 99, 99, 99},
            {18, 21, 26, 66, 99, 99, 99, 99},
            {24, 26, 40, 99, 99, 99, 99, 99},
            {47, 66, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99}
    };

    // helper for dto results
    public static class JpegResult {
        public final long[][] Yq;
        public final long[][] Cbq;
        public final long[][] Crq;
        public final BufferedImage reconstructed;

        public JpegResult(long[][] Yq, long[][] Cbq, long[][] Crq, BufferedImage reconstructed) {
            this.Yq = Yq;
            this.Cbq = Cbq;
            this.Crq = Crq;
            this.reconstructed = reconstructed;
        }
    }

    // Simple Image2 converter (RGB -> YCbCr)
    public static class Image2 {
        // returns {Y, Cb, Cr} clipped to 0..255
        public int[] round(double r, double g, double b) {
            double Yd = 0.299 * r + 0.587 * g + 0.114 * b;
            double Cbd = 128 + (-0.168736 * r - 0.331264 * g + 0.5 * b);
            double Crd = 128 + (0.5 * r - 0.418688 * g - 0.081312 * b);
            int Y = (int) Math.round(Yd);
            int Cb = (int) Math.round(Cbd);
            int Cr = (int) Math.round(Crd);
            Y = Math.max(0, Math.min(255, Y));
            Cb = Math.max(0, Math.min(255, Cb));
            Cr = Math.max(0, Math.min(255, Cr));
            return new int[]{Y, Cb, Cr};
        }
    }

    // ---------- Per-image compressor callable ----------
    public static class JpegCompressor implements Callable<JpegResult> {

        private final BufferedImage image;

        public JpegCompressor(BufferedImage image) {
            this.image = image;
        }

        @Override
        public JpegResult call() throws Exception {
            final int width = image.getWidth();
            final int height = image.getHeight();

            // allocate channels: note original code used [x][y] indexing
            int[][] Y = new int[width][height];
            int[][] Cb = new int[width][height];
            int[][] Cr = new int[width][height];

            Image2 conv = new Image2();

            // RGB -> YCbCr
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int rgb = image.getRGB(x, y);
                    double r = (rgb >> 16) & 0xFF;
                    double g = (rgb >> 8) & 0xFF;
                    double b = (rgb) & 0xFF;
                    int[] chans = conv.round(r, g, b);
                    Y[x][y] = chans[0];
                    Cb[x][y] = chans[1];
                    Cr[x][y] = chans[2];
                }
            }


            int lumaWidth = ((width + 7) / 8) * 8;
            int lumaHeight = ((height + 7) / 8) * 8;
            long[][] Yq = new long[lumaWidth][lumaHeight];

            List<Future<BlockResult>> lumaFutures = new ArrayList<>();

            for (int bx = 0; bx < lumaWidth; bx += 8) {
                for (int by = 0; by < lumaHeight; by += 8) {
                    final int X = bx, Yb = by;
                    // create task
                    Callable<BlockResult> task = () -> {
                        int[][] block = new int[8][8];
                        for (int i = 0; i < 8; i++) {
                            for (int j = 0; j < 8; j++) {
                                int px = X + i;
                                int py = Yb + j;
                                block[i][j] = (px < width && py < height) ? Y[px][py] : 0;
                            }
                        }
                        double[][] d = dct(8, 8, block);
                        long[][] q = quantizationMatrix(d, LUMINANCE_MATRIX);
                        return new BlockResult(X, Yb, q);
                    };
                    lumaFutures.add(jpegPool.submit(task));
                }
            }

            // wait & write results
            for (Future<BlockResult> f : lumaFutures) {
                BlockResult br = f.get();
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int px = br.bx + i;
                        int py = br.by + j;
                        if (px < lumaWidth && py < lumaHeight) {
                            Yq[px][py] = br.data[i][j];
                        }
                    }
                }
            }


            final int chromaW = (width + 1) / 2;
            final int chromaH = (height + 1) / 2;

            long[][] Cbq = new long[chromaW][chromaH];
            long[][] Crq = new long[chromaW][chromaH];

            List<Future<BlockResultChroma>> chromaFutures = new ArrayList<>();


            int chromaTileW = ((width + 15) / 16) * 16;
            int chromaTileH = ((height + 15) / 16) * 16;

            for (int bx = 0; bx < chromaTileW; bx += 16) {
                for (int by = 0; by < chromaTileH; by += 16) {
                    final int BX = bx, BY = by;
                    Callable<BlockResultChroma> task = () -> {
                        int[][] tile = new int[16][16];
                        for (int i = 0; i < 16; i++) {
                            for (int j = 0; j < 16; j++) {
                                int px = BX + i;
                                int py = BY + j;
                                tile[i][j] = (px < width && py < height) ? Cr[px][py] : 0;
                            }
                        }

                        int[][] down = downsample(16, 16, tile);
                        double[][] d = dct(8, 8, down);
                        long[][] q = quantizationMatrix(d, CHROMINANCE_MATRIX);

                        return new BlockResultChroma(BX / 2, BY / 2, q);
                    };

                    chromaFutures.add(jpegPool.submit(() -> {

                        BlockResultChroma resCr = task.call();
                        return resCr;
                    }));
                    chromaFutures.add(jpegPool.submit(() -> {
                        // compute for Cb (same process but reading Cb)
                        int[][] tile = new int[16][16];
                        for (int i = 0; i < 16; i++) {
                            for (int j = 0; j < 16; j++) {
                                int px = BX + i;
                                int py = BY + j;
                                tile[i][j] = (px < width && py < height) ? Cb[px][py] : 0;
                            }
                        }
                        int[][] down = downsample(16, 16, tile);
                        double[][] d = dct(8, 8, down);
                        long[][] q = quantizationMatrix(d, CHROMINANCE_MATRIX);
                        return new BlockResultChroma(BX / 2, BY / 2, q);
                    }));
                }
            }


            for (int fi = 0; fi < chromaFutures.size(); fi += 2) {
                BlockResultChroma brCr = chromaFutures.get(fi).get();
                long[][] datCr = brCr.data;
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int x = brCr.bx + i;
                        int y = brCr.by + j;
                        if (x < chromaW && y < chromaH) Crq[x][y] = datCr[i][j];
                    }
                }

                BlockResultChroma brCb = chromaFutures.get(fi + 1).get();
                long[][] datCb = brCb.data;
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int x = brCb.bx + i;
                        int y = brCb.by + j;
                        if (x < chromaW && y < chromaH) Cbq[x][y] = datCb[i][j];
                    }
                }
            }


            long[][] Ydec = new long[width][height]; // final pixel values after inverse
            List<Future<BlockResultDecoded>> decFutures = new ArrayList<>();
            for (int bx = 0; bx < lumaWidth; bx += 8) {
                for (int by = 0; by < lumaHeight; by += 8) {
                    final int BX = bx, BY = by;
                    Callable<BlockResultDecoded> task = () -> {
                        long[][] qBlock = new long[8][8];
                        for (int i = 0; i < 8; i++)
                            for (int j = 0; j < 8; j++) {
                                int px = BX + i, py = BY + j;
                                qBlock[i][j] = (px < lumaWidth && py < lumaHeight) ? Yq[px][py] : 0;
                            }
                        long[][] unq = unquantizationMatrix(qBlock, LUMINANCE_MATRIX);
                        long[][] id = inversedct(8, 8, unq);
                        return new BlockResultDecoded(BX, BY, id);
                    };
                    decFutures.add(jpegPool.submit(task));
                }
            }

            for (Future<BlockResultDecoded> f : decFutures) {
                BlockResultDecoded br = f.get();
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int px = br.bx + i, py = br.by + j;
                        if (px < width && py < height) Ydec[px][py] = br.data[i][j];
                    }
                }
            }

            long[][] Cbdec_full = new long[width][height];
            long[][] Crdec_full = new long[width][height];

            List<Future<BlockResultDecodedChroma>> chromaDecFutures = new ArrayList<>();

            int chroma8W = ((chromaW + 7) / 8) * 8;
            int chroma8H = ((chromaH + 7) / 8) * 8;

            for (int bx = 0; bx < chroma8W; bx += 8) {
                for (int by = 0; by < chroma8H; by += 8) {
                    final int BX = bx, BY = by;

                    Callable<BlockResultDecodedChroma> tCr = () -> {
                        long[][] qBlock = new long[8][8];
                        for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) {
                            int px = BX + i, py = BY + j;
                            qBlock[i][j] = (px < chromaW && py < chromaH) ? Crq[px][py] : 0;
                        }
                        long[][] unq = unquantizationMatrix(qBlock, CHROMINANCE_MATRIX);
                        long[][] id = inversedct(8, 8, unq);
                        return new BlockResultDecodedChroma(BX, BY, id);
                    };

                    Callable<BlockResultDecodedChroma> tCb = () -> {
                        long[][] qBlock = new long[8][8];
                        for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) {
                            int px = BX + i, py = BY + j;
                            qBlock[i][j] = (px < chromaW && py < chromaH) ? Cbq[px][py] : 0;
                        }
                        long[][] unq = unquantizationMatrix(qBlock, CHROMINANCE_MATRIX);
                        long[][] id = inversedct(8, 8, unq);
                        return new BlockResultDecodedChroma(BX, BY, id);
                    };
                    chromaDecFutures.add(jpegPool.submit(tCr));
                    chromaDecFutures.add(jpegPool.submit(tCb));
                }
            }


            for (int i = 0; i < chromaDecFutures.size(); i += 2) {
                BlockResultDecodedChroma rCr = chromaDecFutures.get(i).get();
                BlockResultDecodedChroma rCb = chromaDecFutures.get(i + 1).get();

                for (int yy = 0; yy < 8; yy++) {
                    for (int xx = 0; xx < 8; xx++) {
                        long vCr = rCr.data[xx][yy];
                        long vCb = rCb.data[xx][yy];

                        int chromaX = rCr.bx + xx;
                        int chromaY = rCr.by + yy;


                        int fx = chromaX * 2;
                        int fy = chromaY * 2;

                        if (fx < width && fy < height) {
                            // write 2x2 (guard edges)
                            Cbdec_full[fx][fy] = vCb;
                            Crdec_full[fx][fy] = vCr;
                        }
                        if (fx + 1 < width && fy < height) {
                            Cbdec_full[fx + 1][fy] = vCb;
                            Crdec_full[fx + 1][fy] = vCr;
                        }
                        if (fx < width && fy + 1 < height) {
                            Cbdec_full[fx][fy + 1] = vCb;
                            Crdec_full[fx][fy + 1] = vCr;
                        }
                        if (fx + 1 < width && fy + 1 < height) {
                            Cbdec_full[fx + 1][fy + 1] = vCb;
                            Crdec_full[fx + 1][fy + 1] = vCr;
                        }
                    }
                }
            }

            BufferedImage reconstructed = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    double newR = Ydec[x][y] + 1.402 * (Crdec_full[x][y] - 128);
                    double newG = Ydec[x][y] - 0.344136 * (Cbdec_full[x][y] - 128) - 0.714136 * (Crdec_full[x][y] - 128);
                    double newB = Ydec[x][y] + 1.772 * (Cbdec_full[x][y] - 128);

                    int r = Math.max(0, Math.min(255, (int) Math.round(newR)));
                    int g = Math.max(0, Math.min(255, (int) Math.round(newG)));
                    int b = Math.max(0, Math.min(255, (int) Math.round(newB)));

                    int rgb = (r << 16) | (g << 8) | b;
                    reconstructed.setRGB(x, y, rgb);
                }
            }

            return new JpegResult(Yq, Cbq, Crq, reconstructed);
        }


        private static class BlockResult {
            final int bx, by;
            final long[][] data;
            BlockResult(int bx, int by, long[][] data) { this.bx = bx; this.by = by; this.data = data; }
        }
        private static class BlockResultChroma {
            final int bx, by;
            final long[][] data;
            BlockResultChroma(int bx, int by, long[][] data) { this.bx = bx; this.by = by; this.data = data; }
        }
        private static class BlockResultDecoded {
            final int bx, by;
            final long[][] data;
            BlockResultDecoded(int bx, int by, long[][] data) { this.bx = bx; this.by = by; this.data = data; }
        }
        private static class BlockResultDecodedChroma {
            final int bx, by;
            final long[][] data;
            BlockResultDecodedChroma(int bx, int by, long[][] data) { this.bx = bx; this.by = by; this.data = data; }
        }
    }


    public static final double pi = 3.14159265;

    public static int [][] downsample(int height,int width,int [][]channelMatrix){
        int [][] matrix= new int[height/2][width/2];
        for(int y=0;y+1<height;y+=2){
            for(int x=0;x+1<width;x+=2){
                int downsample= (channelMatrix[y][x]+channelMatrix[y+1][x+1]+channelMatrix[y+1][x]+channelMatrix[y][x+1])/4;
                matrix[y/2][x/2]=downsample;
            }
        }
        return matrix;
    }

    public static double [][]dct(int height,int width,int [][] matrix){
        double [][] dctMatrix= new double[height][width];
        double ci, cj, dct1, sum;
        for(int i=0;i<height;i++){
            for(int y=0;y<width;y++){
                if (i == 0)
                    ci = 1 / Math.sqrt(8);
                else
                    ci = Math.sqrt(2) / Math.sqrt(8);
                if (y == 0)
                    cj = 1 / Math.sqrt(8);
                else
                    cj = Math.sqrt(2) / Math.sqrt(8);
                sum=0;
                for(int r=0;r<8;r++){
                    for(int p=0;p<8;p++){
                        dct1= (matrix[r][p]-128)*Math.cos((2 * r + 1) * i * pi / (16)) *
                                Math.cos((2 * p + 1) * y * pi / (16));
                        sum=dct1+sum;
                    }
                }
                dctMatrix[i][y]=ci*cj*sum;
            }
        }
        return dctMatrix;
    }

    public static long [][]inversedct(int height,int width,long [][] matrix){
        long [][] F = new long[height][width];
        double sum, ci, cj;

        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {

                sum = 0;

                for (int r = 0; r < height; r++) {
                    ci = (r == 0) ? 1 / Math.sqrt(2) : 1;

                    for (int p = 0; p < width; p++) {
                        cj = (p == 0) ? 1 / Math.sqrt(2) : 1;

                        sum += ci * cj * matrix[r][p]
                                * Math.cos((2 * x + 1) * r * Math.PI / 16)
                                * Math.cos((2 * y + 1) * p * Math.PI / 16);
                    }
                }

                F[x][y] = Math.round(sum / 4 + 128);
            }
        }

        return F;
    }

    public static long [][]quantizationMatrix(double [][]dctMatrix,double[][] quantMatrix){
        long [][]newmatri= new long[8][8];
        for(int i=0;i<8;i++){
            for(int y=0;y<8;y++){
                newmatri[i][y] = Math.round(dctMatrix[i][y]/quantMatrix[i][y]);
            }
        }
        return newmatri;
    }
    public  static long [][]unquantizationMatrix(long [][]dctMatrix,double[][] quantMatrix){
        long [][]newmatri= new long[8][8];
        for(int i=0;i<8;i++){
            for(int y=0;y<8;y++){
                newmatri[i][y] = Math.round(dctMatrix[i][y]*quantMatrix[i][y]);
            }
        }
        return newmatri;
    }


    public static void main(String[] args) throws Exception {
        long end2=0;
        List<BufferedImage> inputs = new ArrayList<>();
        for(int q=0;q<2;q++) {
            long start = -1 * System.nanoTime();

            if (args.length == 0) {
                for (int i = 0; i < 16; i++) {
                    inputs.add(ImageIO.read(new File("src/largephoto.png")));
                }


            } else {
                for (String p : args) {
                    inputs.add(ImageIO.read(new File(p)));
                }
            }

            List<Future<JpegResult>> futures = new ArrayList<>();
            for (BufferedImage img : inputs) {
                futures.add(jpegPool.submit(new JpegCompressor(img)));
            }

            // get results and show windows
            int idx = 0;

            for (Future<JpegResult> f : futures) {
                JpegResult res = f.get();
                final BufferedImage out = res.reconstructed;
            /*

            SwingUtilities.invokeLater(() -> {
                JFrame frame = new JFrame("Reconstructed image");
                frame.add(new JLabel(new ImageIcon(out)));
                frame.pack();
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            });

             */


                // System.out.println("Image " + (idx++) + " done.");
            }


            futures.clear(); // Release references for garbage collection

            long end = System.nanoTime() + start;

            List<BufferedImage> input2 = new ArrayList<>();
            long start2 = -1 * System.nanoTime() + end;

            if (args.length == 0) {
                for (int i = 0; i < 16; i++) {
                    input2.add(ImageIO.read(new File("src/largephoto.png")));
                }


            } else {
                for (String p : args) {
                    input2.add(ImageIO.read(new File(p)));
                }
            }

            List<Future<JpegResult>> futures2 = new ArrayList<>();
            for (BufferedImage img : input2) {
                futures2.add(jpegPool.submit(new JpegCompressor(img)));
            }

            // get results and show windows
            int idx2 = 0;

            for (Future<JpegResult> f : futures2) {
                JpegResult res = f.get();
                final BufferedImage out = res.reconstructed;
            /*

            SwingUtilities.invokeLater(() -> {
                JFrame frame = new JFrame("Reconstructed image");
                frame.add(new JLabel(new ImageIcon(out)));
                frame.pack();
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            });

             */


                // System.out.println("Image " + (idx++) + " done.");
            }

            futures2.clear();

           end2 = System.nanoTime() + start2;
           inputs.clear();
           input2.clear();
           System.gc();
        }
        jpegPool.shutdown();

        System.out.println((float)(end2)/(float)(1000000000));


    }
}


