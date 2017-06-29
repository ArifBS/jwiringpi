import jwiringpi.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;

public class SH1106SPI extends JWiringPiController {
    final public int OLED_WIDTH                                    = 128;
    final public int OLED_HEIGHT                                   = 64;
    final public int NUM_PAGES                                     = 8;
    final public int RST_PIN                                       = 24;
    final public int DC_PIN                                        = 27;
    final public int CHANNEL                                       = 0;
    
    // 1 byte = 8 pixles
    public byte frameBuffer[] = new byte[OLED_WIDTH * OLED_HEIGHT / 8];

    private void display() {
        int page;
        int index = 0;        // index for frameBuffer
        byte[] pageBuffer = new byte[OLED_WIDTH];
        for (page = 0; page < 8; page++) {
            for (int i = 0; i < OLED_WIDTH; i++) {
                pageBuffer[i] = frameBuffer[index];
                index++;
            }
            // set page address 
            sendCommand(0xB0 + page);
            // set low column address 
            sendCommand(0x02); 
            // set high column address 
            sendCommand(0x10); 
            // write data 
            digitalWrite(DC_PIN, HIGH);
            wiringPiSPIDataRW(CHANNEL, pageBuffer, pageBuffer.length); 
        }
    }

    public void showString(String string, int x, int y, int fontSize, Font font, boolean color) {
        BufferedImage image = new BufferedImage(OLED_WIDTH, OLED_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setFont(font);
        g2d.drawString(string, x, y);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), new int[image.getWidth() * image.getHeight()], 0, image.getWidth());
        for (int i = 0; i < image.getHeight(); i++) {
            for (int j = 0; j < image.getWidth(); j++) {
                if (pixels[i * image.getWidth() + j] == -1) {
                    drawPixelToFrameBuffer(j, i, color);
                }
            }
        }
        display();
    }

    public void showString(String string, int x, int y, int fontSize, boolean color) {
        showString(string, x, y, fontSize, new Font("consolas", Font.PLAIN, fontSize), color);
    }

    public void showString(String string, int x, int y) {
        showString(string, x, y, 12, new Font("consolas", Font.PLAIN, 12), true);
    }

    public void showMonoBitmap(int x, int y, short[] bmpBuffer,
        int width, int height, boolean color) {

        int byteWidth = (width + 7) / 8;
        int index = 0;
        for(int j = 0; j < OLED_HEIGHT; j++) {
            for(int i = 0; i < OLED_WIDTH; i ++) {
                if((bmpBuffer[j * byteWidth + i / 8] & 0xFF & (128 >> (i & 7))) != 0) {
                    drawPixelToFrameBuffer(x + i, y + j, color); }
            }
        }    
    }

    public void showMonoBitmap(int x, int y, int[] bmpBuffer,
        int width, int height, boolean color) {
        int byteWidth = (width + 7) / 8;
        int index = 0;
        for(int j = 0; j < OLED_HEIGHT; j++) {
            for(int i = 0; i < OLED_WIDTH; i ++) {
                if((bmpBuffer[j * byteWidth + i / 8] & 0xFF & (128 >> (i & 7))) != 0) {
                    drawPixelToFrameBuffer(x + i, y + j, color); }
            }
        }    
        display();
    }

    public void showImage(int x, int y, File file) {
        if (! (file.exists() && file.isFile())) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), new int[image.getWidth() * image.getHeight()], 0, image.getWidth());
            for (int i = 0; i < image.getHeight(); i++) {
                for (int j = 0; j < image.getWidth(); j++) {
                    int red = (int)(((pixels[i * image.getWidth() + j] & 0xFF0000) >> 16) * 0.3);
                    int green = (int)(((pixels[i * image.getWidth() + j] & 0xFF00) >> 8) * 0.59);
                    int blue = (int)((pixels[i * image.getWidth() + j] & 0xFF) * 0.11);
                    if (red + green + blue < 128) {        // convert RGB to grayscale
                        drawPixelToFrameBuffer(j, i, true);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        display();
    }

    public void drawPixelToFrameBuffer(int x, int y, boolean color) {
        if(x >= OLED_WIDTH || y >= OLED_HEIGHT) {
            return;
        }
        if (color) {
            frameBuffer[x + (y / 8) * OLED_WIDTH] |= (byte)(1 << (y % 8));
        } else {
            frameBuffer[x + (y / 8) * OLED_WIDTH] &= (byte)(~(1 << (y % 8)));
        }
    }

    public void sendCommand(int command) {
        byte[] commands = {(byte)(command & 0xFF)};
        digitalWrite(DC_PIN, LOW);
        wiringPiSPIDataRW(CHANNEL, commands, 1);
    }

    public void begin() {
        pinMode(RST_PIN,OUTPUT);
        pinMode(DC_PIN,OUTPUT);
        wiringPiSPISetup(CHANNEL, 2000000);    //2M
        
        digitalWrite(RST_PIN,HIGH);
        delay(10);
        digitalWrite(RST_PIN,LOW);
        delay(10);
        digitalWrite(RST_PIN,HIGH);

        sendCommand(0xAE);//--turn off oled panel
        sendCommand(0x02);//---set low column address
        sendCommand(0x10);//---set high column address
        sendCommand(0x40);//--set start line address  Set Mapping RAM Display Start Line (0x00~0x3F)
        sendCommand(0x81);//--set contrast control register
        sendCommand(0xA0);//--Set SEG/Column Mapping     
        sendCommand(0xC0);//Set COM/Row Scan Direction   
        sendCommand(0xA6);//--set normal display
        sendCommand(0xA8);//--set multiplex ratio(1 to 64)
        sendCommand(0x3F);//--1/64 duty
        sendCommand(0xD3);//-set display offset    Shift Mapping RAM Counter (0x00~0x3F)
        sendCommand(0x00);//-not offset
        sendCommand(0xd5);//--set display clock divide ratio/oscillator frequency
        sendCommand(0x80);//--set divide ratio, Set Clock as 100 Frames/Sec
        sendCommand(0xD9);//--set pre-charge period
        sendCommand(0xF1);//Set Pre-Charge as 15 Clocks & Discharge as 1 Clock
        sendCommand(0xDA);//--set com pins hardware configuration
        sendCommand(0x12);
        sendCommand(0xDB);//--set vcomh
        sendCommand(0x40);//Set VCOM Deselect Level
        sendCommand(0x20);//-Set Page Addressing Mode (0x00/0x01/0x02)
        sendCommand(0x02);//
        sendCommand(0xA4);// Disable Entire Display On (0xa4/0xa5)
        sendCommand(0xA6);// Disable Inverse Display On (0xa6/a7) 
        sendCommand(0xAF);//--turn on oled panel
    }

    public void clearScreen() {
        clearFrameBuffer(false);
        display();
    }

    public void clearScreen(boolean color) {
        clearFrameBuffer(color);
        display();
    }


    public void clearFrameBuffer(boolean color) {
        if (color) {
            for (int i = 0; i < frameBuffer.length; i++) {
                frameBuffer[i] = (byte)0xFF;
            }
        } else {
            for (int i = 0; i < frameBuffer.length; i++) {
                frameBuffer[i] = 0;
            }
        }
    }

    public void clearFrameBuffer() {
        clearFrameBuffer(false);
    }

    // A monocolor bitmap example for easy migration from some embedded hardware.
    final public int[] WAVESHARE_LOGO = {
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x11,0x11,0x00,0x9C,0x64,0x42,0x1C,0x78,0x7A,0x78,0xEF,0xB8,0x30,0x89,0x8C,0x70,
        0x19,0x91,0x88,0x9C,0xF4,0x63,0x1E,0x78,0x7A,0x79,0xEF,0x3C,0x78,0x89,0x9E,0xF0,
        0x09,0x91,0x88,0xB1,0x84,0x63,0x12,0x40,0x42,0x43,0x02,0x24,0xCC,0xC9,0x30,0x80,
        0x09,0x91,0x88,0xB1,0x84,0x67,0x12,0x40,0x42,0x42,0x02,0x24,0x84,0xC9,0x20,0x80,
        0x09,0x92,0x89,0xB0,0x84,0x65,0x12,0x40,0x42,0x42,0x02,0x24,0x84,0xC9,0x20,0xC0,
        0x0A,0xB2,0x4D,0x1C,0xC7,0xE5,0x96,0x78,0x7A,0x72,0x02,0x2C,0x84,0xA9,0x20,0x60,
        0x0A,0xA2,0x45,0x14,0x67,0xE4,0x9C,0x70,0x72,0x52,0x02,0x38,0x84,0xA9,0x20,0x30,
        0x0A,0x63,0xC5,0x30,0x34,0x67,0x94,0x40,0x42,0x42,0x02,0x28,0x84,0xB9,0x20,0x10,
        0x06,0x67,0xC7,0x30,0x14,0x6F,0x92,0x40,0x42,0x42,0x02,0x24,0x84,0x99,0x20,0x10,
        0x06,0x64,0x66,0x30,0x14,0x68,0x92,0x40,0x42,0x43,0x02,0x24,0x8C,0x99,0x20,0x10,
        0x06,0x64,0x22,0x3D,0xB4,0x68,0xD3,0x78,0x7A,0x79,0xE2,0x26,0x78,0x89,0xBE,0xF0,
        0x04,0x44,0x22,0x1C,0xE4,0x48,0x51,0x78,0x7A,0x79,0xE2,0x22,0x70,0x89,0x9E,0xE0,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x3F,0xFF,0xF0,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x3F,0xFF,0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x3F,0xFF,0xFF,0x80,0x00,0x08,0x30,0x00,0x00,0x00,0x00,0x10,0x00,0x00,0x00,0x00,
        0x3F,0xFF,0xF7,0xC0,0x06,0xDB,0x30,0x3F,0xFF,0xFC,0x00,0x70,0x00,0x7F,0xFF,0xF0,
        0x3F,0xFF,0xE7,0xE0,0x0E,0xDB,0x30,0x3F,0xFF,0xFC,0x7F,0xFF,0xF8,0x7F,0xFF,0xF0,
        0x3F,0xFF,0xC7,0xF0,0x0C,0xDB,0x7E,0x00,0x18,0x00,0x7F,0xFF,0xF8,0x7F,0xFF,0xF0,
        0x3F,0xFF,0x87,0xF0,0x1C,0xDB,0xFE,0x00,0x18,0x00,0x7F,0xFF,0xF8,0x00,0x00,0xF0,
        0x3F,0xFF,0x07,0xF8,0x1C,0xDB,0xF0,0x3F,0xFF,0xFC,0x60,0x70,0x38,0x00,0x01,0xE0,
        0x3F,0xFF,0x87,0xFC,0x18,0xDB,0xC0,0x3F,0xFF,0xF8,0x60,0x70,0x38,0x00,0x01,0xC0,
        0x20,0xC3,0x86,0x3C,0x06,0xFF,0x00,0x30,0x18,0x18,0x60,0x70,0x38,0x00,0x03,0xC0,
        0x30,0xC3,0x0C,0x3C,0x06,0xFF,0x0C,0x30,0x18,0x18,0x60,0x70,0x38,0x00,0x03,0x80,
        0x30,0x43,0x0C,0x1E,0x0E,0xFF,0x6C,0x33,0x18,0xD8,0x60,0x70,0x38,0x07,0xFF,0x00,
        0x30,0x00,0x1C,0x1E,0x0E,0x00,0x6C,0x33,0x9B,0xD8,0x70,0x70,0x38,0x07,0xFF,0x00,
        0x38,0x00,0x08,0x0E,0x1C,0xFE,0x7C,0x30,0x18,0x18,0x7F,0xFF,0xF8,0x07,0xFE,0x00,
        0x38,0x00,0x08,0x0E,0x1C,0xFE,0x3C,0x30,0x19,0x18,0x7F,0xFF,0xF8,0x07,0xFE,0x00,
        0x38,0x00,0x00,0x06,0x1C,0xFE,0x38,0x31,0x9B,0x98,0x60,0x70,0x38,0x00,0x0E,0x00,
        0x3C,0x10,0x00,0x86,0x0C,0x00,0x38,0x33,0x99,0xD8,0x60,0x70,0x38,0x00,0x0E,0x00,
        0x3C,0x10,0x00,0x82,0x0C,0x00,0x38,0x00,0x00,0x00,0x60,0x70,0x38,0x00,0x0E,0x00,
        0x3C,0x18,0x41,0x86,0x0C,0x7E,0x38,0x00,0x00,0x00,0x60,0x70,0x38,0xFF,0xFF,0xF0,
        0x1E,0x38,0x41,0x86,0x0C,0x7E,0x18,0x3F,0xFF,0xF8,0x60,0x70,0x38,0xFF,0xFF,0xF0,
        0x1E,0x38,0xE3,0x8E,0x0C,0x7E,0x18,0x3F,0xFF,0xF8,0x60,0x70,0x38,0xFF,0xFF,0xF0,
        0x1E,0x7C,0xE2,0x0E,0x0C,0x66,0x38,0x00,0x00,0x18,0x7F,0xFF,0xF8,0x00,0x0E,0x00,
        0x1F,0xFF,0xFE,0x1E,0x0C,0x66,0x38,0x00,0x00,0x18,0x7F,0xFF,0xF0,0x00,0x0E,0x00,
        0x0F,0xFF,0xFE,0x0E,0x0C,0x66,0x38,0x1F,0xFF,0xF8,0x7F,0xFF,0xF0,0x00,0x0E,0x00,
        0x0F,0xFF,0xFE,0x1E,0x0C,0x66,0x3C,0x1F,0xFF,0xF8,0x00,0x70,0x00,0x00,0x0E,0x00,
        0x07,0xFF,0xFE,0x3E,0x0C,0x66,0x3C,0x10,0x00,0x38,0x00,0x70,0x00,0x00,0x0E,0x00,
        0x03,0xFF,0xFE,0x7E,0x0C,0x66,0x7C,0x00,0x00,0x18,0x00,0x70,0x00,0x00,0x1E,0x00,
        0x01,0xFF,0xFE,0xFE,0x0C,0xE7,0xEE,0x00,0x00,0x18,0x00,0x7F,0xF8,0x3F,0xFE,0x00,
        0x00,0xFF,0xFF,0xFE,0x0C,0xE7,0xEE,0x3F,0xFF,0xF8,0x00,0x3F,0xF8,0x3F,0xFE,0x00,
        0x00,0x7F,0xFF,0xFE,0x0C,0xC7,0xC6,0x3F,0xFF,0xF8,0x00,0x3F,0xF8,0x3F,0xFC,0x00,
        0x00,0x1F,0xFF,0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x03,0xFF,0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
        0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    };
}
