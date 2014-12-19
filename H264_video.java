package com.example.tcp_client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class Test_video extends Activity implements SurfaceHolder.Callback {
	private PlayerThread mPlayer = null;
	private TCPclient mTCPclient;
	
	BlockingQueue<Frame> queue = new ArrayBlockingQueue<Frame>(100);
	private static class Frame
    {
        public int id;
        public byte[] frameData;

        public Frame(int id)
        {
            this.id = id;
        }
    }
	
	public static byte[] SPS = null;
    public static byte[] PPS = null;
    public static int frameID = 0;
    boolean firstTime = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurfaceView sv = new SurfaceView(this);
		sv.getHolder().addCallback(this);
		setContentView(sv);
		
		new ConnectTask().execute("");
	}

	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (mPlayer == null) {
			mPlayer = new PlayerThread(holder.getSurface());
			mPlayer.start();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mPlayer != null) {
			mPlayer.interrupt();
		}
	}
	
    /**========================================================================*/
    /** This function gets the starting index of the first appearance of match array in source array. The function will search in source array from startIndex position.*/
    public static int find(byte[] source, byte[] match, int startIndex) 
    {  
        if(source == null || match == null)
        {
            Log.d("EncodeDecode", "ERROR in find : null");
            return -1;
        }
        if(source.length == 0 || match.length == 0)
        {
            Log.d("EncodeDecode", "ERROR in find : length 0");
            return -1;
        }
        
        int ret = -1;  
        int spos = startIndex;  
        int mpos = 0;  
        byte m = match[mpos];  
        for( ; spos < source.length; spos++ ) 
        {  
            if(m == source[spos]) 
            {  
                // starting match  
                if(mpos == 0)  
                    ret = spos;  
                // finishing match  
                else if(mpos == match.length - 1)  
                    return ret;  

                mpos++;  
                m = match[mpos];  
            }  
            else 
            {  
                ret = -1;  
                mpos = 0;  
                m = match[mpos];  
            }  
        }  
        return ret;  
    }


    /**========================================================================*/
    /** For H264 encoding, this function will retrieve SPS & PPS from the given data and will insert into SPS & PPS global arrays. */
    public static void getSPS_PPS(byte[] data, int startingIndex)
    {
        byte[] spsHeader = {0x00, 0x00, 0x00, 0x01, 0x27};
        byte[] ppsHeader = {0x00, 0x00, 0x00, 0x01, 0x28};
        byte[] frameHeader = {0x00, 0x00, 0x00, 0x01};

        int spsStartingIndex = -1;
        int nextFrameStartingIndex = -1;
        int ppsStartingIndex = -1;

        spsStartingIndex = find(data, spsHeader, startingIndex);
        Log.d("EncodeDecode", "spsStartingIndex: " + spsStartingIndex);
        if(spsStartingIndex >= 0)
        {
            nextFrameStartingIndex = find(data, frameHeader, spsStartingIndex+1);
            int spsLength = 0;
            if(nextFrameStartingIndex>=0)
                spsLength = nextFrameStartingIndex - spsStartingIndex;
            else
                spsLength = data.length - spsStartingIndex;
            if(spsLength > 0)
            {
                SPS = new byte[spsLength];
                System.arraycopy(data, spsStartingIndex, SPS, 0, spsLength);
            }
        }

        ppsStartingIndex = find(data, ppsHeader, startingIndex);
        Log.d("EncodeDecode", "ppsStartingIndex: " + ppsStartingIndex);
        if(ppsStartingIndex >= 0)
        {
            nextFrameStartingIndex = find(data, frameHeader, ppsStartingIndex+1);
            int ppsLength = 0;
            if(nextFrameStartingIndex>=0)
                ppsLength = nextFrameStartingIndex - ppsStartingIndex;
            else
                ppsLength = data.length - ppsStartingIndex;
            if(ppsLength > 0)
            {
                PPS = new byte[ppsLength];
                System.arraycopy(data, ppsStartingIndex, PPS, 0, ppsLength);
            }
        }
    }


    /**========================================================================*/
    /** Prints the byte array in hex */
    private void printByteArray(byte[] array)
    {
        StringBuilder sb1 = new StringBuilder();
        for (byte b : array) 
        {
            sb1.append(String.format("%02X ", b));
        }
        Log.d("EncodeDecode", sb1.toString());
    }

    /**========================================================================*/
    /** When TCP receives a frame this function is called with the frame data as its parameter*/
    private void encode(byte[] data)
    {
        Log.d("EncodeDecode", "ENCODE FUNCTION CALLED");
                Frame frame = new Frame(frameID);
                int dataLength = 0;

                // If SPS & PPS is not ready then 
                if( (SPS == null || SPS.length ==0) || (PPS == null || PPS.length == 0))
                    getSPS_PPS(data, 0);	//returns position of sps and pps and safes them globally

                dataLength = data.length;
                
                frame.frameData = new byte[dataLength];		//create new frame
                System.arraycopy(data, 0 , frame.frameData, 0, dataLength);	//fill it with data

                // for testing
                Log.d("EncodeDecode" , "Frame no :: " + frameID + " :: frameSize:: " + frame.frameData.length + " :: ");
                printByteArray(frame.frameData);

                // if sps & pps are ready then put the frame in the queue
                if(SPS != null && PPS != null && SPS.length != 0 && PPS.length != 0)
                {
                    Log.d("EncodeDecode", "enqueueing frame no: " + (frameID));

                    try
                    {
                        queue.put(frame);
                    }
                    catch(InterruptedException e)
                    {
                        Log.e("EncodeDecode", "interrupted while waiting");
                        e.printStackTrace();
                    }
                    catch(NullPointerException e)
                    {
                        Log.e("EncodeDecode", "frame is null");
                        e.printStackTrace();
                    }
                    catch(IllegalArgumentException e)
                    {
                        Log.e("EncodeDecode", "problem inserting in the queue");
                        e.printStackTrace();
                    }

                    Log.d("EncodeDecode", "frame enqueued. queue size now: " + queue.size());
                }
                frameID++;
    }

	private class PlayerThread extends Thread {
		private MediaCodec decoder;
		private Surface surface;

		public PlayerThread(Surface surface) {
			this.surface = surface;
		}

		@Override
        public void run() 
        {
            while(SPS == null || PPS == null || SPS.length == 0 || PPS.length == 0)
            {
                try 
                {
                    Log.e("EncodeDecode", "DECODER_THREAD:: sps,pps not ready yet");
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();

                }
            }

            Log.d("EncodeDecode", "DECODER_THREAD:: sps,pps READY");

            
                decoder = MediaCodec.createDecoderByType("video/avc");
                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 640, 480);
                mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(SPS));
                mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(PPS));
                decoder.configure(mediaFormat, surface /* surface */, null /* crypto */, 0 /* flags */);
            

            if (decoder == null) 
            {
                Log.e("DecodeActivity", "DECODER_THREAD:: Can't find video info!");
                return;
            }

            decoder.start();
            Log.d("EncodeDecode", "DECODER_THREAD:: decoder.start() called");

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();


            int i = 0;
            while(!Thread.interrupted())
            {
                Frame currentFrame = null;
                try 
                {
                    Log.d("EncodeDecode", "DECODER_THREAD:: calling queue.take(), if there is no frame in the queue it will wait");
                    currentFrame = queue.take();
                } 
                catch (InterruptedException e) 
                {
                    Log.e("EncodeDecode","DECODER_THREAD:: interrupted while PlayerThread was waiting for the next frame");
                    e.printStackTrace();
                }

                if(currentFrame == null)
                    Log.e("EncodeDecode","DECODER_THREAD:: null frame dequeued");
                else
                    Log.d("EncodeDecode","DECODER_THREAD:: " + currentFrame.id + " no frame dequeued");

                if(currentFrame != null && currentFrame.frameData != null && currentFrame.frameData.length != 0)
                {
                    Log.d("EncodeDecode", "DECODER_THREAD:: decoding frame no: " + i + " , dataLength = " + currentFrame.frameData.length);

                    int inIndex = 0; 
                    while ((inIndex = decoder.dequeueInputBuffer(1)) < 0)
                        ;

                    if (inIndex >= 0) 
                    {
                        Log.d("EncodeDecode", "DECODER_THREAD:: sample size: " + currentFrame.frameData.length);

                        ByteBuffer buffer = inputBuffers[inIndex];
                        buffer.clear();
                        buffer.put(currentFrame.frameData);
                        decoder.queueInputBuffer(inIndex, 0, currentFrame.frameData.length, 33, 0);

                        BufferInfo info = new BufferInfo();
                        int outIndex = decoder.dequeueOutputBuffer(info, 100000);

                        switch (outIndex) 
                        {
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.e("EncodeDecode", "DECODER_THREAD:: INFO_OUTPUT_BUFFERS_CHANGED");
                            outputBuffers = decoder.getOutputBuffers();
                            break;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.e("EncodeDecode", "DECODER_THREAD:: New format " + decoder.getOutputFormat());

                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.e("EncodeDecode", "DECODER_THREAD:: dequeueOutputBuffer timed out!");
                            break;
                        default:
                            Log.d("EncodeDecode", "DECODER_THREAD:: decoded SUCCESSFULLY!!!");
                            ByteBuffer outbuffer = outputBuffers[outIndex];
                            decoder.releaseOutputBuffer(outIndex, true);
                            break;
                        }
                        i++;
                    }
                }
            }

            decoder.stop();
            decoder.release();

        }
    }
	
    public class ConnectTask extends AsyncTask<String, String, TCPclient> {

		@Override
		protected TCPclient doInBackground(String... message) {
			//we create a TCPClient object
			mTCPclient = new TCPclient(new TCPclient.OnMessageReceived() {
				@Override
				//here the messageReceived method is implemented
				public void messageReceived(String message) {
					//this method calls the onProgressUpdate
					publishProgress(message);
				}
			});
			mTCPclient.run();

			return null;
		}
		@Override
		protected void onProgressUpdate(String... values) {
			super.onProgressUpdate(values);
			byte[] b;
			try {
				b = values[0].getBytes("UTF-8");
				Log.v("inkomende data",new String(b, "UTF-8"));
				encode(b);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				Log.d("test","errorshit");
			}
		}
	}
}
