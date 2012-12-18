package org.helioviewer.viewmodel.view.jp2view;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.helioviewer.base.logging.Log;
import org.helioviewer.base.math.Interval;
import org.helioviewer.base.message.Message;
import org.helioviewer.viewmodel.changeevent.ChangeEvent;
import org.helioviewer.viewmodel.changeevent.SubImageDataChangedReason;
import org.helioviewer.viewmodel.view.CachedMovieView;
import org.helioviewer.viewmodel.view.cache.ImageCacheStatus;
import org.helioviewer.viewmodel.view.cache.ImageCacheStatus.CacheStatus;
import org.helioviewer.viewmodel.view.jp2view.J2KRender.RenderReasons;
import org.helioviewer.viewmodel.view.jp2view.JHVJP2View.ReaderMode;
import org.helioviewer.viewmodel.view.jp2view.image.JP2ImageParameter;
import org.helioviewer.viewmodel.view.jp2view.io.http.HTTPRequest;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPDataInputStream;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPDataSegment;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPQuery;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPRequest;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPRequestField;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPResponse;
import org.helioviewer.viewmodel.view.jp2view.io.jpip.JPIPSocket;
import org.helioviewer.viewmodel.view.jp2view.kakadu.JHV_KduException;
import org.helioviewer.viewmodel.view.jp2view.kakadu.JHV_Kdu_cache;
import org.helioviewer.viewmodel.view.jp2view.kakadu.KakaduUtils;

import org.helioviewer.jhv.gui.components.MoviePanel;

/**
 * This class has two different purposes. The first is to connect to and
 * retrieve image data from a JPIP server (if the image is remote). The second
 * is that all view-changed signals are routed through this thread... so it must
 * forward them to the J2KRender thread through that threads signal.
 * 
 * TODO The server may change the parameters of the request, and we should take
 * it into account...
 * 
 * @author caplins
 * @author Juan Pablo
 * @author Markus Langenberg
 */
public class J2KReader implements Runnable   {
    /**
     * There could be multiple reason that the Reader object was signaled. This
     * enum lists them.
     */
    public enum ReaderReasons {
        PLAY, PAUSE, FPS, OTHER
    };
	
    /** Whether IOExceptions and other messages, should be shown or not */
    private static final boolean verbose = false;

    /** The thread that this object runs on. */
    private volatile Thread myThread;

    /** A boolean flag used for stopping the thread. */
    private volatile boolean stop;

    /** A reference to the JP2Image this object is owned by. */
    private JP2Image parentImageRef;

    /** A reference to the JP2ImageView this object is owned by. */
    private JHVJP2View parentViewRef;

    /** The JPIPSocket used to connect to the server. */
    private JPIPSocket socket;

    /** The a reference to the cache object used by the run method. */
    private JHV_Kdu_cache cacheRef;    
    
    /** 
     * Percentage variation of the bandwidth. 
     * If occurs a variation greater or equal than this percentage 
     * must update the bandwidth on the server.
     * Valuable between 0 and 1.
     * */
    //private static final double BW_PERCENT = 0.25;    

    /**
     * The estimated, last and average bandwidth.
     * A negative value means that there is not a previous
     * valid value to take into account.
     */   
    private double bw = -1;    
    //private double bw_last = -1;    
    public static double bw_last = -1;
    private double bw_avg = -1;
    private double bw_error = 0;
    private double bw_error_over_estimated = 0;
    private double bw_error_low_estimated = 0;
    
    /** The time when the last response was received. */
    private long time_initial;    
    private long time_end;
    
    /** The size of the response from the server for a second. */
    private long responseSize = 0;   
    
    private JP2ImageParameter prevParams = null;    
    private JP2ImageParameter currParams = null;
    private int prevCompositionLayer = -1;
    private int currFps = J2KRender.getMovieSpeed();
    private int prevFps;    
   
    /**
     * The constructor. Creates and connects the socket if image is remote.
     * 
     * @param _imageViewRef
     * @throws IOException
     * @throws JHV_KduException
     */
    J2KReader(JHVJP2View _imageViewRef) throws IOException, JHV_KduException {
        parentViewRef = _imageViewRef;

        // These two vars are only here for convenience sake.
        parentImageRef = parentViewRef.jp2Image;
        cacheRef = parentImageRef.getCacheRef();

        // Attempts to connect socket if image is remote.
        if (parentImageRef.isRemote()) {       	
            socket = parentImageRef.getSocket();

            if (socket == null) {
            	socket = new JPIPSocket();               
    			JPIPResponse res = (JPIPResponse) socket.connect(parentImageRef.getURI());
    			cacheRef.addJPIPResponseData(res);            	
            }
            
            if (!parentImageRef.isMultiFrame())
                KakaduUtils.updateServerCacheModel(socket, cacheRef, true);            
            
        } else {
            socket = null;
        }

        myThread = null;
        stop = false;
    }

    /** Starts the J2KReader thread. */
    void start() {
        if (myThread != null)
            stop();
        myThread = new Thread(this, "J2KReader");
        stop = false;
        myThread.start();
    }

    /** Stops the J2KReader thread. */
    synchronized void stop() {
        if (myThread != null && myThread.isAlive()) {
            try {
                stop = true;

                do {
                    myThread.interrupt();
                    myThread.join(100);
                } while (myThread.isAlive());

            } catch (InterruptedException ex) {
                ex.printStackTrace();

            } catch (NullPointerException e) {
            } finally {
                myThread = null;
            }
        }
    }

    /** Releases the resources associated with this object. */
    void abolish() {
        stop();

        try {
            if (socket != null) {            
            	printForDebug("[abolish][socket.close][CID: " + socket.getJpipChannelID() + "] . isMainView: " + parentViewRef.isMainView);                
            	socket.close();
                socket = null;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public boolean isConnected() {
        return (socket != null && socket.isConnected());
    }

    /** Calculate the estimated bandwidth between client and server **/
    private boolean mbwControl(long size, String cid)
    {
    	boolean notify = false;

    	if (size <= 0) return false;
    	
    	// Calculate the elapsed time
    	double time = (double)(time_end - time_initial)/1000.0;
    	
    	// Increment the size of the response
    	responseSize += (size * 8);  	
    	  	
    	if (time >= 0.1) {
    		// Calculate the actual bandwidth
    		bw = (double)responseSize / time;
    		
    		/****/
    		// Calculate the average bandwidth
    		//bw_avg = (bw_last != -1)? (bw + bw_last) / 2.0 : bw;
    		// TEST    		
    		System.out.format("\n[" + cid +  "] bw: %10.4f KB/s \tbw_last: %10.4f KB/s\n", ((bw/8)/1024), ((bw_last/8)/1024));
    		System.out.println("bw: " + bw);
    		System.out.println("bw_last: " + bw_last);
    		
    		bw_avg = (bw_last != -1)? ((0.1*bw) + (0.9*bw_last)): bw;    		
    		    		
    		bw_error = (bw_error != 0)? (bw_error + (Math.abs(bw - bw_avg)/Math.max(bw, bw_avg)))/2:Math.abs(bw - bw_avg)/Math.max(bw, bw_avg);
    		
    		if (bw > bw_avg){
    			bw_error_over_estimated = (bw_error_over_estimated != 0)? (bw_error_over_estimated + (Math.abs(bw - bw_avg)/bw))/2:Math.abs(bw - bw_avg)/bw;
    		}
    		
    		if (bw_avg > bw){
    			bw_error_low_estimated = (bw_error_low_estimated != 0)? (bw_error_low_estimated + (Math.abs(bw_avg - bw)/bw_avg))/2:Math.abs(bw_avg - bw)/bw_avg;
    		}
    		
    		System.out.println("\n[" + cid +  "] bw_error_over_estimated: " + bw_error_over_estimated);
    		System.out.println("[" + cid +  "] bw_error_low_estimated: " + bw_error_low_estimated);
    		
    		System.out.format("\n[" + cid +  "] bw: %10.4f KB/s \tbw_avg: %10.4f KB/s \tbw_error: %10.4f %%", ((bw/8)/1024), ((bw_avg/8)/1024), bw_error);
            // Update the last bandwidth
            bw_last = (bw_last != -1)? (bw_last + bw_avg)/2: bw_avg;
    		
    		//if (!MoviePanel.isPlaying) return false;
   		
       		double setpoint = (((MoviePanel.maximumAccessibleFrameNumberInitial + 1) - MoviePanel.currentFrameNumberInitial))/Double.valueOf(MoviePanel.speedSpinner.getValue().toString());
       		
    		//if ((MoviePanel.view != null) && (MoviePanel.view.getMaximumAccessibleFrameNumber()<MoviePanel.view.getMaximumFrameNumber())){
    		if ((MoviePanel.view != null) && (MoviePanel.isPlaying) && (MoviePanel.view.getMaximumAccessibleFrameNumber()<MoviePanel.view.getMaximumFrameNumber())){    			
        		
    			System.out.println("\n[" + cid +  "] CurrentFrameNumber: " + MoviePanel.view.getCurrentFrameNumber());
    			System.out.println("[" + cid +  "] MaximumAccessibleFrameNumber: " + (MoviePanel.view.getMaximumAccessibleFrameNumber()+1));
    			System.out.println("[" + cid +  "] Difference between frames: " + ((MoviePanel.view.getMaximumAccessibleFrameNumber()+1) - MoviePanel.view.getCurrentFrameNumber()));
    			
        		double measured_value = ((MoviePanel.view.getMaximumAccessibleFrameNumber()+1) - MoviePanel.view.getCurrentFrameNumber())/Double.valueOf(MoviePanel.speedSpinner.getValue().toString());
           		System.out.println("[" + cid +  "] Setpoint: " + setpoint);
        		System.out.println("[" + cid +  "] Measured Value: " + measured_value);           		
        		System.out.flush();       		
        		
        		// Método TCP
        		if (measured_value < setpoint) {
        			System.out.println("1. KB/s: " + ((bw_avg/8)/1024));        			

        			double x1 = 0;
            		double y1 = 0.99;
            		double x2 = setpoint;
            		double y2 = 0.01;
            		
            		double factor_corrector = ((y1-y2)/(x1-x2))*measured_value + y1 - ((y1-y2)/(x1-x2)*x1); 
        			System.out.println("[" + cid +  "] factor_corrector: " + factor_corrector);
        			
        			if (bw_error > 0){
        				
        				//bw_avg = bw_avg - bw_avg*(bw_error);        			
        				bw_avg = bw_avg - bw_avg*(factor_corrector); 
        				
        				//bw_avg = bw_avg - bw_avg*(bw_error);
        				//bw_avg = bw_avg - bw_avg*(bw_error / factor_corrector);
        				//bw_avg = bw_avg - bw_avg*(bw_error_over_estimated);
        				//bw_avg = bw_avg - bw_avg*(bw_error_low_estimated);
        				//bw_avg = bw_avg - bw_avg*(bw_error_low_estimated + factor_corrector);        				
        			}
        			System.out.println("[" + cid +  "] [measured_value < setpoint] New bw_avg: " + bw_avg);
        			System.out.println("[" + cid +  "] 2. KB/s: " + ((bw_avg/8)/1024));
        		}
        		
        		if (measured_value > setpoint){
        			System.out.println("[" + cid +  "] 1. KB/s: " + ((bw_avg/8)/1024));
        			
        			if (bw_error > 0){
        				bw_avg = bw_avg + bw_avg*(bw_error);
        				//bw_avg = bw_avg + bw_avg*(bw_error_low_estimated);
        				//bw_avg = bw_avg + bw_avg*(bw_error_over_estimated);
        			}
        			
        			System.out.println("[" + cid +  "] [measured_value > setpoint] New bw_avg: " + bw_avg);
        			System.out.println("[" + cid +  "] 2. KB/s: " + ((bw_avg/8)/1024));
        		}
    		}
    		/****/
    		
    		System.out.format("\n[" + cid +  "][bandwidth] Size: %10d bits \t Time: %10.4f seg.\t bw: %10s.\n", responseSize, time, mbwUnit(bw));
    		//System.out.format("[" + cid +  "][bandwidth] Size: %10d bits \t Time: %10.4f seg.\t bw: %10s. \t bw_last: %10s. \t bw_avg: %10s.\n", responseSize, time, mbwUnit(bw), mbwUnit(bw_last), mbwUnit(bw_avg));
            System.out.flush();
            
    		/****/
            // TEST
            //if ((bw_last == -1) || ((bw_last != -1) && (Math.abs(bw_avg - bw_last) >= BW_PERCENT*bw_avg))) {
                notify = true;
                
                // Update the last bandwidth
                //bw_last = bw_avg;
            //}
        	/****/
            
            // Initialize the size of the response
            responseSize = 0;
            
            // Update the initial time
            time_initial = time_end;
    	}
    	
		if ((!MoviePanel.isPlaying) && (MoviePanel.view != null)) {
    		System.out.println("[" + cid +  "] MaximumAccesibleFrameNumber: " + MoviePanel.view.getMaximumAccessibleFrameNumber());
    		return false;    			
		}
    	
    	return notify;
    }
    
    public void printTimeStamp(String message){
		long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");
        System.out.println(formatter.format(calendar.getTime()) + " [" + message + "]");
    }
    
    /** Calculate the unit of the bandwidth */
    private String mbwUnit(double bw_value)
    {
		/****/
    	/*
    	String s;    	

    	if (bw_value >= 1099511627776.0){		// 1024*1024*1024*1024
    	    s = String.valueOf((long)bw_value >> 40 );
    	    s = s + "T";
    	} else if (bw_value >= 1073741824.0) {	// 1024*1024*1024    		
    	    s = String.valueOf((long)bw_value >> 30);
    	    s = s + "G";
    	} else if (bw_value >= 1048576.0) {		// 1024*1024			
    	    s = String.valueOf((long)bw_value >> 20);
    	    s = s + "M";    		
    	} else if (bw_value >= 1024.0) {    		
    	    s = String.valueOf((long)bw_value >> 10);
    	    s = s + "K";    		
    	} else {
    	    s = String.valueOf((long)bw_value);
    	}

    	return s;
    	*/
    	
    	// TEST
    	// The calculation not have been performed. (The above calculation imply loss of precision)
    	// The value is returned in bits.	
    	return String.valueOf((long)bw_value);
		/****/
    }
    
    /** Create a JPIP query */
    private JPIPQuery createQuery(JP2ImageParameter currParams, int iniLayer, int endLayer, boolean sendMbw) {
        JPIPQuery query = new JPIPQuery();
  
        query.setField(JPIPRequestField.CONTEXT.toString(), "jpxl<" + iniLayer + "-" + endLayer + ">");
        query.setField(JPIPRequestField.LAYERS.toString(), String.valueOf(currParams.qualityLayers));

        Rectangle resDims = currParams.resolution.getResolutionBounds();

        query.setField(JPIPRequestField.FSIZ.toString(), String.valueOf(resDims.width) + "," + String.valueOf(resDims.height) + "," + "closest");
        query.setField(JPIPRequestField.ROFF.toString(), String.valueOf(currParams.subImage.x) + "," + String.valueOf(currParams.subImage.y));
        query.setField(JPIPRequestField.RSIZ.toString(), String.valueOf(currParams.subImage.width) + "," + String.valueOf(currParams.subImage.height));        
        
        if (bw_avg != -1 && sendMbw) {
            query.setField(JPIPRequestField.MBW.toString(), mbwUnit(bw_avg));
            query.setField(JPIPRequestField.SRATE.toString(), String.valueOf(J2KRender.getMovieSpeed()));
            query.setField(JPIPRequestField.DRATE.toString(), "1");
        }
        return query;
    }
    
    /** Create an updated JPIP query */
    private JPIPQuery createUpdatedQuery() {    	
    	JPIPQuery updated_query = new JPIPQuery();    	
        updated_query.setField(JPIPRequestField.MBW.toString(), mbwUnit(bw_avg));
        updated_query.setField(JPIPRequestField.SRATE.toString(), String.valueOf(J2KRender.getMovieSpeed()));
        updated_query.setField(JPIPRequestField.DRATE.toString(), "1");                                    	
        return updated_query;    	
    }    
    
    /** Send a query */
    private void sendQuery() throws IOException {
    	JPIPRequest req = new JPIPRequest(HTTPRequest.Method.GET);
    	Interval<Integer> layers = parentImageRef.getCompositionLayerRange();    	
        JPIPQuery query = new JPIPQuery();                          
        
        int curLayer = currParams.compositionLayer;        
        int endLayer = curLayer == 0 ? layers.getEnd() : curLayer - 1;
        
    	/****/
    	/*
        // If video is playing, anticipate the current layer
    	if (parentViewRef.getRender().getMovieMode() && layers.getEnd()>J2KRender.getMovieSpeed()) {
        	curLayer = currParams.compositionLayer + J2KRender.getMovieSpeed();
        	if (curLayer > layers.getEnd()) curLayer = curLayer - layers.getEnd();
        }
    	int endLayer = curLayer == 0 ? layers.getEnd() : curLayer - 1;
    	*/
    	/****/
        
        /****/
        // TEST
        /*
        if (parentViewRef.isMainView) {
            query = createQuery(currParams, curLayer, endLayer, parentViewRef.getRender().getMovieMode());	
        } else {
        	query = createQuery(currParams, curLayer, curLayer, parentViewRef.getRender().getMovieMode());
        } 
        */    
        if (parentViewRef.isMainView) {
            query = createQuery(currParams, curLayer, endLayer, true);	
        } else {
        	query = createQuery(currParams, curLayer, curLayer, false);
        }        
        /****/
                                                   		
        req.setQuery(query.toString());
        socket.send(req);
        printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "][sendQuery]: " + query.toString());
    }
    
    /** Send an updated query */
    private void sendUpdatedQuery() throws IOException {
    	JPIPRequest req = new JPIPRequest(HTTPRequest.Method.GET);    	
        JPIPQuery query = new JPIPQuery();
       	query = createUpdatedQuery();
        req.setQuery(query.toString());
        socket.send(req);
        printForDebug("\t\t\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "][sendUpdatedQuery]: " + query.toString());
    }
    
    /** Check, whether view parameters have changed */
    private boolean viewHasChanged() {
    	boolean viewChanged;    	

        prevParams = currParams;
        currParams = parentViewRef.getImageViewParams();
        viewChanged = prevParams == null || !(currParams.subImage.equals(prevParams.subImage) && currParams.resolution.equals(prevParams.resolution) && currParams.qualityLayers == prevParams.qualityLayers);
        
    	if (!parentViewRef.isMainView) {
            viewChanged = viewChanged || currParams.compositionLayer != prevCompositionLayer;
            prevCompositionLayer = currParams.compositionLayer;
        }
    	return viewChanged;
    }
    
    /**  Check, whether the framerate has changed */
    private boolean fpsHasChanged() {
    	boolean fpsChanged;    	

        prevFps = currFps;
        currFps = J2KRender.getMovieSpeed();
        fpsChanged = (prevFps == currFps)?false:true;
        return fpsChanged;
    }

    /** Update the cache status */
    private void updateCacheStatus(CacheStatus status) {
    	Interval<Integer> layers = parentImageRef.getCompositionLayerRange();
        
        if (parentViewRef.isMainView() && parentViewRef instanceof CachedMovieView) {
            ImageCacheStatus cacheStatus = ((CachedMovieView) parentViewRef).getImageCacheStatus();
            for(int j = layers.getEnd(); j >= layers.getStart(); j--) {
                cacheStatus.setImageStatus(j, status);
            }
        }
    }
    
    /** Print a message for debugging purposes */
    private void printForDebug(String s){
    	if (verbose){
    		System.out.println(s);	
    	}    	
    }
    
    public void run() {
        boolean complete = false;
        boolean downgradeNecessary = false;        
        int pending = 0;        
        boolean isReconnected = false;
        
        // Update the cache status
        updateCacheStatus(CacheStatus.HEADER);        
        
        while (!stop) {        	
       	
    		// Wait for signal
            try {
            	printForDebug("\n\t[J2KReader] WaitForSignal. isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode() + ". pending: " + pending);        	
            	parentViewRef.readerSignal.waitForSignal();
            } catch (InterruptedException e) {
                continue;
            }
        	
            // If image is not remote image, do nothing and just signal render
            if (parentViewRef.getReaderMode() == ReaderMode.SIGNAL_RENDER_ONCE) {
                parentViewRef.setReaderMode(ReaderMode.NEVERFIRE);
                parentViewRef.renderRequestedSignal.signal(RenderReasons.NEW_DATA);
            } else if (!parentImageRef.isRemote() && parentViewRef.getReaderMode() != ReaderMode.NEVERFIRE) {
                parentViewRef.renderRequestedSignal.signal(RenderReasons.NEW_DATA);
            } else {            	

            	isReconnected = false;
            	
                // If socket is closed, but communication is necessary, open it
                if (socket != null && socket.isClosed() && (parentViewRef.isPersistent() || viewHasChanged())) {
                    try {
                    	printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "] Connecting with the server. isMainView: " + parentViewRef.isMainView);
                    	socket = new JPIPSocket();
                        socket.connect(parentImageRef.getURI());
                        if (!parentImageRef.isMultiFrame()) {
                            KakaduUtils.updateServerCacheModel(socket, cacheRef, true);
                        }                        
                        isReconnected = true;
                    } catch (IOException e) {
                        if (verbose) {
                            e.printStackTrace();
                        }
                        try {                       	
                        	socket.close();
                        } catch (IOException ioe) {
                            Log.error(">> J2KReader.run() > Error closing socket.", ioe);
                        }
                        parentViewRef.fireChangeEvent(new ChangeEvent(new SubImageDataChangedReason(parentViewRef)));

                        // Send signal to try again
                        parentViewRef.readerSignal.signal(ReaderReasons.OTHER);
                    } catch (JHV_KduException e) {
                        e.printStackTrace();
                    }
                }

                // If socket is open, get image data
                if (socket != null && !socket.isClosed()) {

                    try {
                        JPIPResponse res = null;
                        Interval<Integer> layers = parentImageRef.getCompositionLayerRange();                        
                        
                    	if (viewHasChanged() || isReconnected) {                    		
                    		// If view has changed downgrade caching status
                    		downgradeNecessary = true;                    		
                    		sendQuery();
                            pending++;                            
                            printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  1 Query . isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());
                    	}                    	
                    	
                        // While there are pending queries
                    	while (pending > 0 && !stop) {
                    		printForDebug("\t\t[J2KReader][socket.receive][CID: " + socket.getJpipChannelID() + "] pending: " + pending + ". isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());
                            res = socket.receiveHeader();
                            JPIPDataInputStream jpip;
                            jpip = socket.receiveJPIPDataStream();                                
                            JPIPDataSegment seg;                                                   
                            
                            complete = false;                        
                            time_initial = System.currentTimeMillis();
                            
                            // Receive response     
                            while ((seg=jpip.readSegment())!= null && !stop) {
                                time_end = System.currentTimeMillis();
                                res.addJpipDataSegment(seg);
                                
                                if (res != null) {
                                	
                                	// Calculate estimated bandwidth
                                	//boolean notify = mbwControl(res.getResponseSize());                                		
                                	
                                	/****/
                                	// TEST
                                	boolean notify = false;
                                	if (parentViewRef.isMainView){
                                        //printTimeStamp(String.valueOf(res.getResponseSize()));
                                		notify = mbwControl(res.getResponseSize(), socket.getJpipChannelID());
                                	}
                                	/****/
                                	
                            		/****/
                                	// TEST
                                	// Update estimated bandwidth  (Only in Video Mode)                              
                                	//if (parentViewRef.isMainView && parentViewRef.getRender().getMovieMode() && notify) {
                                	
                                	// Always notify the new mbw
                                	//if (notify && bw_avg!=-1 && parentViewRef.getRender().getMovieMode()) {
                                	if (notify && bw_avg!=-1) {                                		
                                		sendUpdatedQuery();
                                		pending++;
                                		printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  2 Updated Query. isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());
                                    }
                            		/****/
                                        
                                    // Downgrade, if necessary
                                    if (downgradeNecessary && res.getResponseSize() > 0 && parentViewRef.isMainView() && parentViewRef instanceof CachedMovieView) {
                                    	printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  Downgrade.");     
                                            		
                                        ImageCacheStatus cacheStatus = ((CachedMovieView) parentViewRef).getImageCacheStatus();
                                                
                                        for(int i = layers.getEnd(); i >= layers.getStart(); i--) {
                                            cacheStatus.downgradeImageStatus(i);
                                        }                                            
                                        downgradeNecessary = false;                                            
                                    }                                    

                                    // Add response to cache - if query complete, react
                                    if (cacheRef.addJPIPResponseData(res)) {
                                    	printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  Query completed. isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());                                           
                                                
                                        // Mark query as completed                                    
                                        complete = true;                                    

                                        // Tell the cache status
                                        updateCacheStatus(CacheStatus.COMPLETE);
                                    }                                    
                                        
                                    // Fire ChangeEvent, if wanted
                                    if ((parentViewRef.getReaderMode() == ReaderMode.ONLYFIREONCOMPLETE && complete) || parentViewRef.getReaderMode() == ReaderMode.ALWAYSFIREONNEWDATA) {
                                        parentViewRef.renderRequestedSignal.signal(RenderReasons.NEW_DATA);
                                    }                                    
                                }                                
                                
                                // Let others do their work, too
                                Thread.yield();                              
                                
                                // Check if there are any signal 
                                if (parentViewRef.isMainView && (parentViewRef.readerSignal.isSignaled() || Thread.interrupted())) {                                	
                                	
                                	printForDebug("\t\t# parentViewRef.reasonReaderSignal: " + parentViewRef.readerSignal.getReason() + ". pending: " + pending);
                                	
                                	parentViewRef.readerSignal.setSignal(false);                                	
                                	
                                    if (viewHasChanged()) {
                                		downgradeNecessary = true;
                                		sendQuery();
                                        pending++;                                        
                                        printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  3 Query. isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());                                		
                                	} else {
                                    	if (parentViewRef.readerSignal.getReason() == ReaderReasons.FPS && fpsHasChanged()) {
                                    		sendUpdatedQuery();
                                    		pending++;
                                    		printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  4 Updated Query. isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());                                    			
                                    	} else {
                                    		if (parentViewRef.readerSignal.getReason() == ReaderReasons.PLAY || parentViewRef.readerSignal.getReason() == ReaderReasons.PAUSE) {
                                    			sendQuery();
                                    			pending++;
                                    			printForDebug("\t\t[J2KReader][CID: " + socket.getJpipChannelID() + "]  5 Query. isMainView: " + parentViewRef.isMainView + ". Mode: " + parentViewRef.getRender().getMovieMode());	
                                    		}
                                    	}                                		
                                	}  
                                }
                                
                            } // endWhile. (readSegment)                            
                            
                        	pending--;                          	
                            
                        } // endWhile. (pending)                        
                        
                    } catch (IOException e) {                    	
                        if (verbose) {
                            System.out.println(e.getMessage());
                            e.printStackTrace();
                        }
                    	if (socket != null) {
                            try {
                            	printForDebug("[Exception][socket.close][CID: " + socket.getJpipChannelID() + "]  isMainView: " + parentViewRef.isMainView);
                            	socket.close();                            	
                            } catch (IOException ioe) {
                                Log.error(">> J2KReader.run() > Error closing socket", ioe);
                                if (ioe instanceof SocketException && ioe.getMessage().contains("Broken pipe")) {
                                    Message.err("Broken pipe error", "Broken pipe error! This error is a known bug. It occurs when too many movies with too many frames are loaded. Movie playback might not work or will be very slow. Try removing the current layers and load shorter movies or select a larger movie cadence. We are sorry for this inconvenience and are working on the problem.", false);
                                }
                            }
                        }
                        parentViewRef.fireChangeEvent(new ChangeEvent());

                        // Send signal to try again
                        parentViewRef.readerSignal.signal(ReaderReasons.OTHER);      				
                    } catch (JHV_KduException e) {
                        e.printStackTrace();
                    }
                }
            }
        }        
    }    
}