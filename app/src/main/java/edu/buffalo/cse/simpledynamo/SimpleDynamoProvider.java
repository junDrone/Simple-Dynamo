package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {


	String[] portNoList;
	String[] nodeHashID;
	String[] tempHashId;
	HashMap<String,String> queryPendingHashMap;
	HashMap<String,String> starQuerySuccessorCheckHashMap;

	HashMap<String,Integer> queryHashMap;
	public Cursor queryCursor;
	public Cursor globalDumpCursor;
	public int starQueryFlag;
	public int atQueryFlag;

	//DB variables
	private SQLiteDatabase db;
	private Forsql forsql;
	public ContentValues cv;

	String myPort;
	String childStatus;
	int myIndex;
	int localstarQueryFlag;
	Object lock;
	Object querylock;
	Object clientTaskLock;


	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {

		String deleteHashKey=null;
		try{
			deleteHashKey=genHash(selection);
		}catch(NoSuchAlgorithmException e){}

		int pos= whereAreYou(deleteHashKey);

		String deleteMessage="9"+"~"+selection+"~"+"dummy";

		for(int i=0;i<3;i++)
		{
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMessage, portNoList[pos+i]);
		}

		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Uri insert(Uri uri, ContentValues values) {

		Log.v("Insert","Start");
		String key = (String)values.get(forsql.col1);
		String value=(String)values.get(forsql.col2);
		String insertHashKey=null;
		try{
			insertHashKey=genHash(key);
		}catch(NoSuchAlgorithmException e){}

		int pos=whereAreYou(insertHashKey);
		Log.v("Insert","Pos~"+pos);
		String sendInsertMessage="1"+"~"+key+"~"+value;
		new InsertReplicationTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sendInsertMessage, String.valueOf(pos));


		return null;
	}



	@Override
	public boolean onCreate() {

		portNoList=new String[]{"11124","11112","11108","11116","11120","11124","11112","11108","11116"};
		tempHashId=new String[]{"11124","11106","11108","11116","11120","11124","11106","11108","11116"};
		nodeHashID=new String[9];

		forsql=new Forsql(getContext());
		db=forsql.getWritableDatabase();

		queryPendingHashMap=new HashMap<String, String>();
		queryHashMap=new HashMap<String, Integer>();
		starQuerySuccessorCheckHashMap = new HashMap<String, String>();

		lock=new Object();
		querylock= new Object();
		childStatus="IHaveNoChildren";
		localstarQueryFlag=0;
		atQueryFlag=0;
		clientTaskLock=new Object();

		for(int i=0;i<portNoList.length;i++)
		{
			String temp=String.valueOf((Integer.parseInt(portNoList[i])/2));
			try{
				nodeHashID[i]=genHash(temp);}catch(NoSuchAlgorithmException e){}
		}

		//Get My Port
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));

		myIndex=findMe(myPort);

		//Start server thread
		try {
			ServerSocket serverSocket = new ServerSocket(10000);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {

			e.printStackTrace();
			//return;
		}
		//TODO Phase 4 - Failure Handling
		//String queryMessage= "20"+"~"+"Hey dad,"+"~"+"I am your son.";
		//new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMessage, portNoList[myIndex-1]);
		String tname="mytable";
		String count="SELECT COUNT(*) FROM "+tname;
		Cursor getCountCursor = db.rawQuery(count,null);
		Log.v("Create","Number="+getCountCursor.getCount());
		//if(getCountCursor.getCount()>1)
		{
			Log.v("Recovery","Recovery Start");
			String rebornChildMessage="0"+"~"+myPort+"~"+"recoverymessage"+"~";
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, rebornChildMessage, portNoList[myIndex + 2]);
		}


		return false;
	}

	public int findMe(String thePort)
	{
		int i;
		for(i=2;i<7;i++)
		{
			if(thePort.equals(portNoList[i]))
				return i;
		}

		return i;
	}

	private synchronized boolean lookUp(String nodeId,int index) {

		int pred=index-1;
		if(pred==-1)
			pred=4;
		//Log.v("lookup",""+index);
		if (nodeHashID[pred].compareTo(nodeHashID[index]) > 0)
		{

			if (nodeHashID[index].compareTo(nodeId) >= 0 || nodeHashID[pred].compareTo(nodeId) < 0) {
				//Log.v("lookup","pred>me");
				return true;
			}
			else {
				//Log.v("lookup","pred>me-fail");
				return false;
			}
		}

		else if(nodeHashID[index].compareTo(nodeId)>=0 && nodeHashID[pred].compareTo(nodeId)<0)
		{
			return true;
		}

		return false;
	}


	public synchronized int whereAreYou(String nodeId)
	{
		for(int i=2;i<7;i++)
		{
			if(lookUp(nodeId,i)==true)
				return i;
		}
		return 0;
	}

	@Override
	public synchronized Cursor query(Uri uri, String[] projection, String selection,
									 String[] selectionArgs, String sortOrder) {

		/*try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/

		Log.v("Query","Start");
		SQLiteQueryBuilder forcursor=new SQLiteQueryBuilder();
		forcursor.setTables("mytable");
		String queryHashValue=null;
		try{
			queryHashValue=genHash(selection);}catch(NoSuchAlgorithmException e){}
		String[] array= {selection};
		String tname="mytable";
		String key="key";
		String allQuery="SELECT * FROM "+tname+" ;";
		Log.v("Query Start", ""+selection);

		if(selection.equals("@"))
		{
			Log.v("Query","@,*");
			String rebornChildMessage="0"+"~"+myPort+"~"+"recoverymessage"+"~";
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, rebornChildMessage, portNoList[myIndex + 1]);

			atQueryFlag=0;
			while(atQueryFlag==0){
				try
			{
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			}
			Cursor c=db.rawQuery(allQuery, null);
			return c;
		}
		else if(selection.equals("*"))
		{
			//Star Query
			//Global Dump
			Log.v("StarQuery","Begin-"+myPort);
			String globalQueryMessage="6"+"~"+myPort+"~"+"dummy"+"~";
			Cursor c=db.rawQuery(allQuery, null);
			c.moveToFirst();



			if(c.getCount()>0)
			{
				do {

					int keyIndex= c.getColumnIndex("key");
					int valueIndex=c.getColumnIndex("value");
					String keyValue=c.getString(keyIndex);
					String valueValue=c.getString(valueIndex);
					globalQueryMessage=globalQueryMessage+"#"+keyValue+"#"+valueValue;
				}while (c.moveToNext());
			}
			c.close();

			//check whois successor begins

			localstarQueryFlag=0;
			String checkNextNodeMessage="99"+"~"+"Dummy";
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, checkNextNodeMessage, portNoList[myIndex+1]);

			int count=0;
			while(count<4 && localstarQueryFlag==0)
			{
				count++;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if(localstarQueryFlag==1)
			{
				Log.v("StarQuery","Ihavesson  to "+portNoList[myIndex+2]);
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, globalQueryMessage, portNoList[myIndex+1]);
			}
			else
			{
				Log.v("StarQuery","Moving on to "+portNoList[myIndex+2]);
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, globalQueryMessage, portNoList[myIndex+2]);
			}

			//Check who is successor ends


			starQueryFlag=0;
			while(starQueryFlag==0)
			{
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			Log.v("StarQuery","LordMegatronRisesAgain");
			return globalDumpCursor;

		}//star query if clause ends
		else if(lookUp(queryHashValue,myIndex)==true)
		{
			Log.v("*Query","I have it "+key);
			Cursor c=db.query(true, tname, null, "key = ? ", array, null, null, null, null);

			return c;
		}
		else
		{
			int pos= whereAreYou(queryHashValue);
			Log.v("Query:","Hey,"+portNoList[pos]+" give me "+selection);
			String sendQueryMessage="2"+"~"+selection+"~"+myPort;
			//new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sendQueryMessage, portNoList[pos]);
			int flag=0;
			while(flag==0)
			{
				new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sendQueryMessage, String.valueOf(pos));

				queryHashMap.put(selection,0);
           /* while(queryHashMap.get(selection)==0)
            {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
				int count =0;
				while(queryHashMap.get(selection)==0 && count<3) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					count++;
				}
				if(queryHashMap.get(selection)==0)
				{
					queryHashMap.remove(selection);
					continue;
				}
				else{flag=1; break;}

			}
			Log.v("@Query", "LordMegatrinRisesAgain");
			queryHashMap.remove(selection);
			return queryCursor;
		}

	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];
			DataInputStream in;
			Socket sock;
			InputStream is;

			while(true){
				try {
					sock = serverSocket.accept();
					is=sock.getInputStream();
					in =  new DataInputStream(is);
					String recdMess=in.readUTF();
					String[] value_split = recdMess.split("~");
					publishProgress(value_split);


				}catch(Exception e){}}

		}

		protected void onProgressUpdate(String...strings) {

			int type=Integer.parseInt(strings[0].trim());
			Log.v("ServerTask","Type~"+type);

			if(type==0)
			{

				// Recovery Query
				//int retru= db.delete("mytable",null,null);

				String zePort=strings[1].trim();
				if(zePort.equals(myPort))
				{
					Log.v("Recovery", "BacktoSource-" + myPort);

					if (strings.length > 3)
					{

						String allData = strings[3].trim();
						allData = allData.substring(1);
						String[] allDataSplit = allData.split("#");
					//	int retu = db.delete("mytable",null,null);

						if (allDataSplit.length > 1)
						{
							for (int i = 0; i < allDataSplit.length; i += 2)
							{
								String keyToInsert=allDataSplit[i];
								String valueToInsert=allDataSplit[i+1];

								cv=new ContentValues();
								cv.put("key",keyToInsert);
								cv.put("value", valueToInsert);
								long ret=db.insertWithOnConflict("mytable", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
								if(ret>0)
								{
									Log.v("Recover","Success "+keyToInsert);
								}
							}

						}
					}

					atQueryFlag=1;

				}
				else
				{
					String status=null;
					if(strings[1].trim().equals(portNoList[myIndex - 2]) ||strings[1].trim().equals(portNoList[myIndex-1]))
					{
						Log.v("Recover","SourceDataRequired "+myPort);
						status="source";
					}
					else
					{
						Log.v("Recover","SelfDataRequired "+myPort);
						status="self";
					}

					String tname="mytable";
					String allQuery="SELECT * FROM "+tname+" ;";
					Log.v("StarQuery","TakeMyMoney-"+myPort);

					Cursor c=db.rawQuery(allQuery, null);
					c.moveToFirst();

					String queryMessage=null;

					if(strings.length>3)
					{
						Log.v("Recovery","checkIndex-"+myPort);
						queryMessage="0"+"~"+strings[1].trim()+"~"+strings[2].trim()+"~"+ strings[3].trim();
					}
					else
					{
						Log.v("Recovery","checklessIndex-"+myPort);
						queryMessage="0"+"~"+strings[1].trim()+"~"+strings[2].trim()+"~";
					}

					if(c.getCount()>0)
					{
						do{
							int col1=c.getColumnIndex("key");
							int col2=c.getColumnIndex("value");

							String keyValue=c.getString(col1);
							String valueValue=c.getString(col2);
							String keyHashValue=null;
							try{
								keyHashValue=genHash(keyValue);}catch(NoSuchAlgorithmException e){}
							if(status.equals("self") && lookUp(keyHashValue,myIndex)==true)
							{
								queryMessage=queryMessage+"#"+keyValue+"#"+valueValue;
							}
							else if(status.equals("source") && lookUp(keyHashValue,myIndex-2))
							{	Log.v("Recover","SourceData "+keyValue+"~Index="+myIndex);
								queryMessage=queryMessage+"#"+keyValue+"#"+valueValue;
							}

						}while(c.moveToNext());
					}
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMessage, portNoList[myIndex + 1]);
				}



			}

			if(type==1)
			{
				//Insert Message
				String keyToInsert=strings[1].trim();
				String valueToInsert=strings[2].trim();

				cv=new ContentValues();
				cv.put("key",keyToInsert);
				cv.put("value", valueToInsert);
				long ret=db.insertWithOnConflict("mytable", null, cv,SQLiteDatabase.CONFLICT_REPLACE);
				if(ret<0)
					Log.v("IntertFail","FalseGod");
				else
					Log.v("Insert","AllHailMegatron"+"~"+keyToInsert);
				if(queryPendingHashMap.containsKey(keyToInsert))
				{
					Log.v("Insert","Restart query for key "+keyToInsert);
					String queryMessage= "3"+"~"+keyToInsert+"~"+valueToInsert;
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMessage, queryPendingHashMap.get(keyToInsert));
					queryPendingHashMap.remove(keyToInsert);
				}
			}
			else if(type==2)
			{
				//Query Message
				Log.v("QueryMan", "QueryEnter"+strings[1]);
				String queryterm=strings[1].trim();
				String[] array= {queryterm};
				String tname="mytable";
				Cursor c=db.query(true,tname,null,"key = ? ",array,null,null,null,null);

				if(c.getCount()==0)
				{
					Log.v("Query","Waiting for insert to complete");
					queryPendingHashMap.put(queryterm,strings[2].trim());
				}
				else{

					if(c.moveToFirst())
					{
						int col1=c.getColumnIndex("key");
						int col2=c.getColumnIndex("value");

						String keyValue=c.getString(col1);
						String valueValue=c.getString(col2);

						Log.v("QuerySuccess","I Have it "+keyValue);

						String queryMessage= "3"+"~"+keyValue+"~"+valueValue;
						new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMessage, strings[2].trim());
					}
					else
						Log.v("QueryFail","For Some Reason");
				}

			}
			else if(type==3)
			{
				//Query Retrun Message
				synchronized (lock)
				{

					String thevalue = strings[2].trim();
					String thekey = strings[1].trim();
					if(queryHashMap.containsKey(thekey) && queryHashMap.get(thekey)==0)
					{
						Log.v("Query-Origin", myPort + " got the message " + thekey);
						MatrixCursor mc = new MatrixCursor(new String[]{"key", "value"});
						mc.addRow(new Object[]{thekey, thevalue});
						mc.moveToFirst();
						queryCursor = mc;
						queryHashMap.put(thekey, 1);
					}
				}

			}
			else if(type==6)
			{
				// Global Dump Query
				//TODO : fix # count and move tofirst beforeiterating
				String zePort=strings[1].trim();
				if(zePort.equals(myPort)) {
					Log.v("StarQuery", "BacktoSource-" + myPort);
					MatrixCursor globalCursor = new MatrixCursor(new String[]{"key", "value"});
					if (strings.length > 3)
					{

						String allData = strings[3].trim();
						allData = allData.substring(1);
						String[] allDataSplit = allData.split("#");

						if (allDataSplit.length > 1)
						{
							for (int i = 0; i < allDataSplit.length; i += 2)
							{
								globalCursor.addRow(new Object[]{allDataSplit[i], allDataSplit[i + 1]});
							}
							globalCursor.moveToFirst();
						}
					}

					globalDumpCursor=globalCursor;
					starQueryFlag=1;

				}
				else
				{
					String tname="mytable";
					String allQuery="SELECT * FROM "+tname+" ;";
					Log.v("StarQuery","TakeMyMoney-"+myPort);

					Cursor c=db.rawQuery(allQuery, null);
					c.moveToFirst();

					String queryMessage=null;

					if(strings.length>3)
					{
						queryMessage="6"+"~"+strings[1].trim()+"~"+strings[2].trim()+"~"+ strings[3].trim();
					}
					else
					{
						queryMessage="6"+"~"+strings[1].trim()+"~"+strings[2].trim()+"~";
					}

					if(c.getCount()>0)
					{
						do{
							int col1=c.getColumnIndex("key");
							int col2=c.getColumnIndex("value");
							String keyValue=c.getString(col1);
							String valueValue=c.getString(col2);

							queryMessage=queryMessage+"#"+keyValue+"#"+valueValue;

						}while(c.moveToNext());
					}
					//check whois successor begins

					localstarQueryFlag=0;
					String checkNextNodeMessage="99"+"~"+zePort+"~"+zePort;
					Log.v("StarQuery", "" + checkNextNodeMessage);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, checkNextNodeMessage, portNoList[myIndex + 1]);
					new SuccessorCheckTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, zePort, portNoList[myIndex + 1]);
					starQuerySuccessorCheckHashMap.put(zePort,queryMessage);




				}

			}
			else if(type==9)
			{
				Log.v("DeleteMessage","DeleteEnter");
				String []array={strings[1].trim()};
				int ret= db.delete("mytable","key=?",array);

			}
			else if(type==99)
			{
				Log.v("StarQuery","IExistMAte");
				String aliveMessage ="100"+"~"+"dummy"+"~"+strings[1].trim();
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, aliveMessage, portNoList[myIndex-1]);
			}
			else if(type==100)
			{
				Log.v("StarQuery","I have a child");
				localstarQueryFlag=1;
				if(starQuerySuccessorCheckHashMap.containsKey(strings[2].trim()))
				{
					Log.v("StarQuery", "Ihavesson to " + portNoList[myIndex + 1]);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, starQuerySuccessorCheckHashMap.get(strings[2].trim()), portNoList[myIndex + 1]);
					starQuerySuccessorCheckHashMap.remove(strings[2].trim());
				}
			}
			return;

		}
	}//Server task ends

	private class ClientTask extends AsyncTask<String,Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			synchronized (clientTaskLock)
			{
				try {

					Socket socket;
					String portToSend = msgs[1].trim();

					socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(portToSend));
					String msgToSend = msgs[0];
					Log.v("Client For Query", msgToSend+"~" + portToSend);
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					out.writeUTF(msgToSend);
					socket.close();


				} catch (UnknownHostException e) {
					Log.e("ff", "ClientTask UnknownHostException");
				} catch (IOException e) {
					Log.e("ff", "ClientTask socket IOException");
				}

			return null;}
		}
	}//Client task ends

	private class InsertReplicationTask extends AsyncTask<String,Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			try {

				Socket socket;
				int index=Integer.parseInt(msgs[1].trim());
				for(int i=0;i<3;i++)
				{
					String portToSend=portNoList[index+i];

					socket=new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(portToSend));
					String msgToSend = msgs[0];
					Log.v("clientFunc",""+msgToSend+"~"+portToSend);
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					out.writeUTF(msgToSend);
					socket.close();
				}


			} catch (UnknownHostException e) {
				Log.e("ff", "ClientTask UnknownHostException");
			} catch (IOException e) {
				Log.e("ff", "ClientTask socket IOException");
			}

			return null;
		}
	}//Client task ends
	private class QueryTask extends AsyncTask<String,Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			synchronized (querylock){
				try {

					Socket socket;
					int index=Integer.parseInt(msgs[1].trim());
					for(int i=0;i<3;i++)
					{
						String portToSend=portNoList[index+i];

						socket=new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
								Integer.parseInt(portToSend));
						String msgToSend = msgs[0];
						Log.v("QueryFunc",msgToSend+"~"+portToSend);
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						out.writeUTF(msgToSend);
						socket.close();
					}


				} catch (UnknownHostException e) {
					Log.e("ff", "ClientTask UnknownHostException");
				} catch (IOException e) {
					Log.e("ff", "ClientTask socket IOException");
				}


			return null;}
		}
	}//Client task ends

	private class SuccessorCheckTask extends AsyncTask<String,Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			//TODO - Count to 10

			int count=0;
			while(count<4 &&localstarQueryFlag==0 )
			{
				count++;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if(localstarQueryFlag==0)
			{
				String sourcePort=msgs[0].trim();
				String portToSend=portNoList[myIndex+2];
				try {

					Socket socket;
					int index=Integer.parseInt(msgs[1].trim());


						socket=new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
								Integer.parseInt(portToSend));
						String msgToSend = starQuerySuccessorCheckHashMap.get(sourcePort);
						starQuerySuccessorCheckHashMap.remove(sourcePort);
						Log.v("StarclientFunc-NoChild","~"+portToSend);
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						out.writeUTF(msgToSend);
						socket.close();



				} catch (UnknownHostException e) {
					Log.e("ff", "ClientTask UnknownHostException");
				} catch (IOException e) {
					Log.e("ff", "ClientTask socket IOException");
				}

			}

			return null;
		}
	}//Client task ends


}
