package net.sourceforge.gpj.jcremoteterminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;

public class CloudCard extends Card {

	private Socket socket = null;
	private InputStream is = null;
	private OutputStream os = null;
	private ATR atr = null;

	CloudCard(Socket socket) throws CardException
	{
		this.socket = socket;	     	//open socket
		this.atr=null;
		try {
			is = socket.getInputStream();
			os = socket.getOutputStream();
			//get ATR
			os.write(new byte[]{0x00,0x00,0x00,0x00});
			os.flush();
			byte[] atr = sRead(is);
			this.atr = new ATR(atr);
		} catch (IOException e) {
			throw new CardException(e);
		}
	}
	
	@Override
	public void beginExclusive() throws CardException {
		throw new CardException("");
	}

	@Override
	public void disconnect(boolean arg0) throws CardException {
		if(socket==null)
			throw new CardException("");
		try {
			socket.close();
		} catch (IOException e) {
			throw new CardException(e);
		}
	}

	@Override
	public void endExclusive() throws CardException {
		throw new CardException("");
	}

	@Override
	public ATR getATR() {
		return atr;
	}

	private byte[] sRead(InputStream is) throws IOException
	{
		short len = 0;
		byte a;
		byte b;
		byte c;
		byte d;
		int r = is.read();
		if(r==-1)
			throw new IOException();
		a = (byte)r;
		r = is.read();
		if(r==-1)
			throw new IOException();
		b = (byte)r;
		r = is.read();
		if(r==-1)
			throw new IOException();
		c = (byte)r;
		len = (short)(0xFFFF&(r<<8));
		r = is.read();
		if(r==-1)
			throw new IOException();
		d = (byte)r;
		len |= r;
		byte[] pkt = new byte[len+4];
		pkt[0] = a;
		pkt[1] = b;
		pkt[2] = c;
		pkt[3] = d;
		byte[] apdu = new byte[len];
		for(int i=0;i<len;i++)
		{
			r = is.read();
			if(r==-1)
				throw new IOException();
			apdu[i] = (byte)r;
		}
		return apdu;
	}


	@Override
	public CardChannel getBasicChannel() {
		try {
			return new CloudChannel(socket,is,os,this);
		} catch (Exception e) {
		}
		return null;
	}

	@Override
	public String getProtocol() {
		return null;
	}

	@Override
	public CardChannel openLogicalChannel() throws CardException {
		try {
			return new CloudChannel(socket,is,os,this);
		} catch (Exception e) {
			throw new CardException(e);
		}
	}

	@Override
	public byte[] transmitControlCommand(int arg0, byte[] arg1)
			throws CardException {
		try {
			this.atr=null;
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			//get ATR
			os.write(new byte[]{0x00,0x00,0x00,0x00});
			os.flush();
			byte[] atr = sRead(is);
			this.atr = new ATR(atr);
			return atr;
		} catch (Exception e) {
			throw new CardException(e);
		}
	}

}
