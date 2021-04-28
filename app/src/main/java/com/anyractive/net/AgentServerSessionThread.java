package com.anyractive.net;

import android.util.Log;

import com.example.mediapipemultihandstrackingapp.MainActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class AgentServerSessionThread implements Runnable {

    private Thread t;
    private boolean isRunning;
    private Socket socket;
    private IAgentServerSessionListener listener;
    private BufferedReader br = null;
    private BufferedWriter bw = null;
    private IAgentServerSessionListener.CONNECTION_STATUS connectionStatus;

    public AgentServerSessionThread(Socket socket, IAgentServerSessionListener listener)
    {
        this.socket = socket;
        this.listener = listener;
    }

    public void start() throws Throwable
    {
        if(this.isRunning)
        {
            throw new Exception("Already running");
        }
        this.isRunning = true;
        this.t = new Thread(this);
        this.t.start();
    }

    public void stop()
    {
        if(this.t != null) {
            try
            {
                this.isRunning = false;

                t.interrupt();

                if(this.socket != null) {
                    this.socket.close();
                    this.socket = null;
                }


                if(br != null) {
                    br.close();
                    br = null;
                }


                if(bw != null) {
                    bw.close();
                    bw = null;
                }
            }
            catch(Throwable t)
            {

            }
            finally {
                t = null;
                this.listener.onConnectionStatus(this, IAgentServerSessionListener.CONNECTION_STATUS.DISCONNECTED);
            }
        }
    }

    @Override
    public void run()
    {

        try
        {
            this.connectionStatus = IAgentServerSessionListener.CONNECTION_STATUS.CONNECTING;
            this.listener.onConnectionStatus(this, this.connectionStatus);

            this.br = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            this.bw = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));

            this.connectionStatus = IAgentServerSessionListener.CONNECTION_STATUS.CONNECTED;
            this.listener.onConnectionStatus(this, this.connectionStatus);
            Log.e(MainActivity.TAG, "Thread Started");

            char[] buf = new char[1024];
            while(this.isRunning)
            {
                try
                {

                    int readBytes = br.read(buf);
                    this.listener.onPacketReceived(new String(buf, 0, readBytes));
                }
                catch(Throwable t)
                {
                    throw t;
                }
            }
        }
        catch(Throwable t)
        {
            this.stop();
            Log.e(MainActivity.TAG, t.getMessage(), t);
            this.listener.onConnectionStatus(this, IAgentServerSessionListener.CONNECTION_STATUS.DISCONNECTED);
        }
        finally
        {

        }

    }

    public void send(String msg)
    {
        try {
            bw.write(msg);
            bw.flush();
            Log.e(MainActivity.TAG, "Send: " + msg);
        }
        catch(Throwable t)
        {
            Log.e(MainActivity.TAG, t.getMessage(), t);
            this.connectionStatus = IAgentServerSessionListener.CONNECTION_STATUS.DISCONNECTED;
            this.listener.onConnectionStatus(this, this.connectionStatus);
        }
    }
}
