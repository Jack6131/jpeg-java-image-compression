import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.ExecutorService;
import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgcodecs.Imgcodecs;
public class RealTimeCameraJPEG {
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    public static void main(String[] args) throws Exception {
        VideoCapture camera = new VideoCapture(0); // 0 = default webcam
        if (!camera.isOpened()) {
            System.out.println("Cannot open camera");
            return;
        }

        ExecutorService jpegPool = MainMultiImageJPEG.jpegPool; // reuse your pool

        Mat frame = new Mat();
        int frameCount = 0;

        while (true) {
            if (!camera.read(frame)) break;

            // Convert Mat -> BufferedImage
            BufferedImage img = matToBufferedImage(frame);

            // submit frame to jpegPool
            final int currentFrame = frameCount;
            jpegPool.submit(() -> {
                try {
                    MainMultiImageJPEG.JpegCompressor comp = new MainMultiImageJPEG.JpegCompressor(img);
                    MainMultiImageJPEG.JpegResult res = comp.call();

                    // display processed frame (Swing)
                    SwingUtilities.invokeLater(() -> {
                        JFrame f = new JFrame("Processed frame " + currentFrame);
                        f.add(new JLabel(new ImageIcon(res.reconstructed)));
                        f.pack();
                        f.setVisible(true);
                    });

                } catch (Exception e) { e.printStackTrace(); }
            });

            frameCount++;
        }

        camera.release();
    }

    // helper: convert OpenCV Mat to BufferedImage
    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) type = BufferedImage.TYPE_BYTE_GRAY;

        int bufferSize = mat.channels()*mat.cols()*mat.rows();
        byte[] b = new byte[bufferSize];
        mat.get(0,0,b);

        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }
}
