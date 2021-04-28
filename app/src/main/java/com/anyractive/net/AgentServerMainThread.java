package com.anyractive.net;

import android.os.StrictMode;
import android.util.Log;

import com.example.mediapipemultihandstrackingapp.MainActivity;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class AgentServerMainThread implements Runnable, IAgentServerSessionListener {

    private int port = 8081;
    private Thread t;
    private boolean isRunning;
    private ServerSocket serverSocket;
    private List<AgentServerSessionThread> sessionList;

    public AgentServerMainThread()
    {
        this.sessionList = new ArrayList<AgentServerSessionThread>();
    }

    public void start() throws Throwable                    //서버시작
    {
        if(this.isRunning)
        {
            throw new Exception("Already running");
        }
        this.isRunning = true;
        this.t = new Thread(this);
        this.t.start();

        Log.e(MainActivity.TAG, "서버 시작");
    }

//    public void join()
//    {
//        try {
//            this.t.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }

    public void stop()
    {
        if(this.t != null) {
            try
            {
                for(int i=0;i<this.sessionList.size();i++)
                {
                    this.sessionList.get(i).stop();
                }
                this.sessionList.clear();

                if(serverSocket != null)
                {
                    serverSocket.close();
                }
                serverSocket = null;

                this.isRunning = false;
                this.t.interrupt();
                this.t = null;


            }
            catch(Throwable t)
            {

            }
            finally {

            }

            Log.e(MainActivity.TAG, "서버 정지");
        }
    }

    @Override
    public void run()
    {

        try
        {
            StrictMode.ThreadPolicy policy =
                    new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            this.serverSocket = new ServerSocket(this.port);
            Log.e(MainActivity.TAG, "Thread Started [" + this.serverSocket.getLocalSocketAddress() + ":"+this.port+"]");

            while(this.isRunning)
            {
                try
                {
                    Socket socket = this.serverSocket.accept();
                    Log.e(MainActivity.TAG, "클라이언트 소켓 접속함.");
                    AgentServerSessionThread session = new AgentServerSessionThread(socket, this);
                    this.sessionList.add(session);
                    session.start();
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
        }
        finally
        {

        }
    }

    public void broadcast(String msg)
    {
        Log.e(MainActivity.TAG, "Broadcast: " + msg);
        for(int i=0;i<this.sessionList.size();i++)
        {
            this.sessionList.get(i).send(msg);
        }
    }

    @Override
    public void onConnectionStatus(AgentServerSessionThread session, CONNECTION_STATUS connectionStatus) {
        if (connectionStatus == CONNECTION_STATUS.DISCONNECTED) {
            Log.e(MainActivity.TAG, "클라이언트 연결끊김: " + session.hashCode());

            int sessionIdx = -1;
            for(int i=0;i<this.sessionList.size();i++)
            {
                AgentServerSessionThread t = (AgentServerSessionThread)this.sessionList.get(i);
                if(t.hashCode() == session.hashCode())
                {
                    sessionIdx = i;
                    break;
                }
            }
            if(this.sessionList.size() > 0 && sessionIdx >= 0) {
                this.sessionList.remove(sessionIdx);
            }
        }


        if (connectionStatus == CONNECTION_STATUS.CONNECTED) {
            Log.e(MainActivity.TAG, "클라이언트 연결됨: " + session.hashCode());
        }
    }

    @Override
    public void onPacketReceived(String msg) {
        Log.e(MainActivity.TAG, "Receive: " + msg);

    }

}
