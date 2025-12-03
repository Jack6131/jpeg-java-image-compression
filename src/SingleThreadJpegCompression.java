import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class touple{
    int numZeroes,number;
}
class ImagePanel extends JPanel {
    BufferedImage img;

    ImagePanel(BufferedImage img) {
        this.img = img;
        setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(img, 0, 0, null);
    }
}
public class SingleThreadJpegCompression {

    public static long [][]finalChrom;
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

    public static long[][] ChromaSteps(int [][]chromaMatrix,int h1,int w1){
        int height=((h1+16)/16)*16;
        int width =((w1+16)/16)*16;
        long[][] finalChroma = new long[(w1+2)/2][(h1+2)/2];
        for(int w=0;w<width;w+=16){
            for(int h=0;h<height;h+=16){
                int[][] initialChroma = new int[16][16];
                for(int i=w;i<w+16;i++){
                    for(int t=h;t<h+16;t++){
                        if(i<w1&&t<h1){
                        initialChroma[i%16][t%16]=chromaMatrix[i][t];
                        }else{
                            initialChroma[i%16][t%16]=0;
                        }
                    }
                }
                int[][] downsampled= downsample(16,16,initialChroma);
                double[][] dct=  dct(8,8,downsampled);
                long [][] quant= quantizationMatrix(dct,CHROMINANCE_MATRIX);
                for(int i=0;i<8;i++){
                    for(int t=0;t<8;t++){
                        if(i+w/2<(w1+2)/2&&t+ h/2<(h1+2)/2){
                        finalChroma[i+w/2][t+h/2] = quant[i%8][t%8];}



                    }
                }
            }
        }
        return finalChroma;
    }
    public static long[][] LumaSteps(int [][]chromaMatrix,int h1,int w1){
        int height=((h1+8)/8)*8;
        int width =((w1+8)/8)*8;
        long[][] finalChroma = new long[((width+8)/8)*8][((height+8)/8)*8];
       // System.out.println("WIDTH:"+(((width+8)/8)*8));
        //System.out.println("HEIGHT:"+(((height+8)/8)*8));
        for(int w=0;w<width;w+=8){
            for(int h=0;h<height;h+=8){
                //System.out.println("W:"+w);
                //System.out.println("H:"+h);
                int[][] initialChroma = new int[8][8];
                for(int i=w;i<w+8;i++){
                    for(int t=h;t<h+8;t++){
                        if(i<w1&&t<h1){
                            initialChroma[i%8][t%8]=chromaMatrix[i][t];
                        }else{
                            initialChroma[i%8][t%8]=0;
                        }
                    }
                }
                double[][] dct=  dct(8,8,initialChroma);
                long [][] quant= quantizationMatrix(dct,LUMINANCE_MATRIX);
                for(int i=w;i<w+8;i++){
                    for(int t=h;t<h+8;t++){
                        if(i<w1&&t<h1){
                            finalChroma[i][t]=quant[i%8][t%8];
                        }else{
                            finalChroma[i][t]=0;
                        }
                    }
                }
            }
        }
        return finalChroma;
    }
    public static long [][]uncompressfilelum(int h1 ,int w1, long [][]finalchroma){
        int height=((h1+8)/8)*8;
        int width =((w1+8)/8)*8;
        long[][] unchroma = new long[width][height];
        for(int w=0;w<width;w+=8){
            for(int h=0;h<height;h+=8){
                long[][] initialChroma = new long[8][8];
                for(int i=w;i<w+8;i++){
                    for(int t=h;t<h+8;t++){
                        if(i<w1&&t<h1){
                            initialChroma[i%8][t%8]=finalchroma[i][t];
                        }else{
                            initialChroma[i%8][t%8]=0;
                        }
                    }
                }

                long [][] quant= unquantizationMatrix(initialChroma,LUMINANCE_MATRIX);
                long [][] undct= inversedct(8,8,quant);
                for(int i=w;i<w+8;i++){
                    for(int t=h;t<h+8;t++){
                        if(i<w1&&t<h1){
                        unchroma[i][t]=undct[i%8][t%8];}
                        else{
                            unchroma[i][t]=0;
                        }


                    }
                }

            }}
        return unchroma;
    }


    public static long[][] uncompressfilecolor(int h1, int w1, long[][] finalchroma) {


        int chromaH = h1;
        int chromaW = w1;


        int fullH = chromaH * 2;
        int fullW = chromaW * 2;

        long[][] out = new long[fullH][fullW];

        for (int by = 0; by < chromaH; by += 8) {
            for (int bx = 0; bx < chromaW; bx += 8) {

                long[][] block = new long[8][8];


                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int iy = by + y;
                        int ix = bx + x;

                        if (iy < chromaH && ix < chromaW)
                            block[y][x] = finalchroma[iy][ix];
                        else
                            block[y][x] = 0;
                    }
                }

                long[][] quant = unquantizationMatrix(block, CHROMINANCE_MATRIX);
                long[][] idct = inversedct(8, 8, quant);
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        long v = idct[y][x];

                        int fy = (by + y) * 2;
                        int fx = (bx + x) * 2;

                        if (fy+1 < fullH && fx+1 < fullW) {
                            out[fy][fx] = v;
                            out[fy+1][fx] = v;
                            out[fy][fx+1] = v;
                            out[fy+1][fx+1] = v;
                        }
                    }
                }
            }
        }

        return out;
    }

    public static final double pi= 3.14159265;
    //Chroma Down Sample
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

    //DCT Algorithm
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


    public static long [][]threadedCompression(){
        long[][] matri=new long[8][8];
        return matri;

    }


    public ArrayList <touple >zigzagportion(long [][] newmatri ){
        ArrayList<touple> t= new ArrayList<>();
        return t;
    }





    public static void main(String[] args) throws IOException {
        List<BufferedImage> inputs = new ArrayList<>();

        for(int i=0;i<1;i++) {
            inputs.add(ImageIO.read(new File("src/largephoto.png")));
        }

        long start =System.nanoTime();
for(BufferedImage im:inputs){
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_PYCC);
            ColorModel colorModel = new ComponentColorModel(cs, new int[]{8, 8, 8}, false, false,
                    Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);


            int width =im.getWidth();
            int height = im.getHeight();
            WritableRaster raster = colorModel.createCompatibleWritableRaster(width,height);
            BufferedImage reconstructedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Image2 myImage = new Image2();
            int [][]YChannel= new int[width][height];
            int [][]CbChannel= new int[width][height];
            int [][]CrChannel = new int[width][height];

/*
            int[][] matrix = {
                    {52, 55, 61, 66, 70, 61, 64, 73},
                    {63, 59, 55, 90, 109, 85, 69, 72},
                    {62, 59, 68, 113, 144, 104, 66, 73},
                    {63, 58, 71, 122, 154, 106, 70, 69},
                    {67, 61, 68, 104, 126, 88, 68, 70},
                    {79, 65, 60, 70, 77, 68, 58, 75},
                    {85, 71, 64, 59, 55, 61, 65, 83},
                    {87, 79, 69, 68, 65, 76, 78, 94}
            };
            double[][] matri2=dct(8,8,matrix);
            long [][] qmatri2=quantizationMatrix(matri2,LUMINANCE_MATRIX);
            long [][] unquant= unquantizationMatrix(qmatri2,LUMINANCE_MATRIX);
            long [][] matri3= inversedct(8,8,unquant);
            for(int i=0;i<8;i++){
                for (int r=0;r<8;r++){
                    System.out.print(matri3[i][r]+ " ");
                }
                System.out.println();
            }
*/


            for(int x=0;x<width;x++) {
                for (int y = 0; y < height; y++) {
                    int rgb = im.getRGB(x, y);
                    double r = (rgb >> 16) & 0xFF;
                    double g = (rgb >> 8) & 0xFF;
                    double b = rgb & 0xFF;
                    int[] myChannels = myImage.round(r, g, b);
                    int channel1 = (int) Math.max(0, Math.min(255, (int) myChannels[0]));
                    int channel2 = (int) Math.max(0, Math.min(255, (int) myChannels[1]));
                    int channel3 = (int) Math.max(0, Math.min(255, (int) myChannels[2]));
                    YChannel[x][y] = channel1;
                    CbChannel[x][y] = channel2;
                    CrChannel[x][y] = channel3;
                }}
                long [][]Y1=LumaSteps(YChannel,height,width);
                long [][]CR1=ChromaSteps(CrChannel,height,width);
                long [][]CB1=ChromaSteps(CbChannel,height,width);
                Y1=uncompressfilelum(height,width,Y1);
                long [][]CR=uncompressfilecolor(CR1.length, CR1[0].length,CR1);
                long [][] CB=uncompressfilecolor(CB1.length,CB1[0].length,CB1);
                for(int x=0;x<width;x++){
                    for(int y=0;y<height;y++){


                    //Conversion Back
                        double newR = Y1[x][y] + 1.402 * (CR[x][y] - 128);
                        double newG = Y1[x][y]
                                - 0.344136 * (CB[x][y] - 128)
                                - 0.714136 * (CR[x][y] - 128);
                        double newB = Y1[x][y] + 1.772 * (CB[x][y] - 128);



                        int finalR = Math.max(0, Math.min(255, (int) Math.round(newR)));
                    int finalG = Math.max(0, Math.min(255, (int) Math.round(newG)));
                    int finalB = Math.max(0, Math.min(255, (int) Math.round(newB)));


                    int finalRgb = (finalR << 16) | (finalG << 8) | finalB;
                    reconstructedImage.setRGB(x, y, finalRgb);

                    }
                }
                /*
        JFrame f = new JFrame();
        f.add(new ImagePanel(reconstructedImage));
        f.pack();
        f.setVisible(true);*/


            }
        long end= System.nanoTime();
        System.out.println((float)(end-start)/(float)(1000000000));
    }









    }
