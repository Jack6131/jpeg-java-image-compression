import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
public class Main {
    //Chroma Down Sample
    public int [][][] downsample(int height,int width,int [][][]channelMatrix){
        int [][][] matrix= new int[2][height/2][width/2];
        for(int y=0;y+1<height;y+=2){
            for(int x=0;x+1<width;x+=2){
                int cravg= (channelMatrix[1][y][x]+channelMatrix[1][y+1][x+1]+channelMatrix[1][y+1][x]+channelMatrix[1][y][x+1])/4;
                int cbavg= (channelMatrix[2][y][x]+channelMatrix[2][y+1][x+1]+channelMatrix[2][y+1][x]+channelMatrix[2][y][x+1])/4;
                matrix[0][y/2][x/2]=cravg;
                matrix[1][y/2][x/2]=cbavg;
            }
        }
        return matrix;
    }






    public static void main(String[] args) {
        try
        {
            File ogimage= new File("C:\\Users\\Jack\\Desktop\\ImageCompression\\src\\pngtest.png");
            BufferedImage im= ImageIO.read(ogimage);
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_PYCC);
            ColorModel colorModel = new ComponentColorModel(cs, new int[]{8, 8, 8}, false, false,
                    Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
            int width =im.getWidth();
            int height = im.getHeight();
            WritableRaster raster = colorModel.createCompatibleWritableRaster(width,height);
            BufferedImage ycbcr=new BufferedImage(colorModel,raster,false,null);

            BufferedImage yImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            BufferedImage cbImage = new BufferedImage(width, height,BufferedImage.TYPE_BYTE_GRAY);
            BufferedImage crImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            BufferedImage reconstructedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Image2 myImage = new Image2();
            int [][][]ChannelMatrix= new int[3][height][width];
            for(int x=0;x<width;x++) {
                for (int y = 0; y < height; y++) {
                    int rgb = im.getRGB(x,y);
                    double r = (rgb>>16)& 0xFF;
                    double g = (rgb>>8)& 0xFF;
                    double b = rgb & 0xFF;
                    int[] myChannels = myImage.round(r, g, b);
                    int channel1=(int)Math.max(0,Math.min(255,(int)myChannels[0]));
                    int channel2=(int)Math.max(0,Math.min(255,(int)myChannels[1]));
                    int channel3=(int)Math.max(0,Math.min(255,(int)myChannels[2]));
                    int yPixel = (channel1 << 16) | (channel1 << 8) | channel1;
                    int crPixel = (channel2 << 16) | (channel2 << 8) | channel2;
                    int cbPixel = (channel3 << 16) | (channel3 << 8) | channel3;
                    ChannelMatrix[0][y][x]=yPixel;
                    ChannelMatrix[1][y][x]=crPixel;
                    ChannelMatrix[2][y][x]=cbPixel;

                    int greyScale = (channel1<<16)|(channel2<<8)| channel3;

                    //Conversion Back
                    double newR = myChannels[0] + 1.402 * (myChannels[2] - 128);
                    double newG = myChannels[0] - 0.344136 * (myChannels[1] - 128) - 0.714136 * (myChannels[2] - 128);
                    double newB = myChannels[0] + 1.772 * (myChannels[1] - 128);


                    int finalR = Math.max(0, Math.min(255, (int) Math.round(newR)));
                    int finalG = Math.max(0, Math.min(255, (int) Math.round(newG)));
                    int finalB = Math.max(0, Math.min(255, (int) Math.round(newB)));


                    int finalRgb = (finalR << 16) | (finalG << 8) | finalB;
                    reconstructedImage.setRGB(x, y, finalRgb);
                    yImage.setRGB(x, y, yPixel);
                    cbImage.setRGB(x, y, cbPixel);
                    crImage.setRGB(x, y, crPixel);
                    raster.setPixel(x,y,new int[]{yPixel,crPixel,cbPixel});
                }

            }
            ImageIO.write(yImage, "png", new File("y_channel.png"));
            ImageIO.write(cbImage, "png", new File("cb_channel.png"));
            ImageIO.write(crImage, "png", new File("cr_channel.png"));
            ImageIO.write(ycbcr, "png", new File("complete.png"));
            ImageIO.write(reconstructedImage, "jpeg", new File("eww.jpeg"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}