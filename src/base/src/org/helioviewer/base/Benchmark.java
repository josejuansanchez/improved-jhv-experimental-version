package org.helioviewer.base;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

public class Benchmark {
	public static long time_initial;
	
    int curLayer;
    int x;
    int y;
    int width;
    int height;
    byte[] byteBuffer;
    int[] intBuffer;
    String directory;
    
    public Benchmark() {
    }
    
    public void setParameters(byte[] byteBuffer, int x, int y, int width, int height, int curLayer, String directory) {
            this.byteBuffer = byteBuffer;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.curLayer = curLayer;               
            this.directory = directory;
    }
    
    public void setParametersIntBuffer(int[] intBuffer, int x, int y, int width, int height, int curLayer, String directory) {
        this.intBuffer = intBuffer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.curLayer = curLayer;               
        this.directory = directory;
    }

    public void writeToDisk(int x, int y, int w, int h) {          
            
            if (!(this.x == x && this.y == y && width == w && height == h)) return;
           
            long time_current = System.currentTimeMillis();         
            double time_elapsed = (double)(time_current - time_initial)/1000.0;
            
            //System.out.println("Time Elapsed: " + time_elapsed);
            
            String fileName =  directory + String.format("%03d", curLayer) + "_" + x + "_" + y + "_" + width + "_" + height + "." + String.format("%.3f", time_elapsed) + ".dat";
                                            
            try {
                    DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(fileName));
                    fileOut.write(byteBuffer, 0, byteBuffer.length);
            }catch (Exception e) {
                    System.out.println("Exception: " + e.toString());
            }
    }
    
    public void writeFramesToDisk(int x, int y, int w, int h) {            
   	 
            if (!(this.x == x && this.y == y && width == w && height == h)) return;         
            
            String fileName =  directory + String.format("%03d", curLayer) + "_" + x + "_" + y + "_" + width + "_" + height + ".dat";
                                            
            try {
                    DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(fileName));
                    fileOut.write(byteBuffer, 0, byteBuffer.length);
            }catch (Exception e) {
                    System.out.println("Exception: " + e.toString());
            }
    }  

}