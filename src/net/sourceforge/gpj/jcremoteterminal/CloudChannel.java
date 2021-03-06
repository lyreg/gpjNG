package net.sourceforge.gpj.jcremoteterminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

public class CloudChannel extends CardChannel {

	private InputStream is = null;
	private OutputStream os = null;
	private Socket socket = null;
	private CloudCard card = null;
	
	CloudChannel(Socket socket, InputStream is, OutputStream os, CloudCard card) throws IOException
	{
		this.is = is;
     	this.os = os;
     	this.card = card;
     	this.socket = socket;
	}
	
	@Override
	public void close() throws CardException {
		try {
			socket.close();
		} catch (IOException e) {
			throw new CardException(e);
		}
	}

	@Override
	public Card getCard() {
		return card;
	}

	@Override
	public int getChannelNumber() {
		return 0;
	}

	private byte[] sRead() throws IOException
	{
		int len = 0;
		byte a;
		int r = is.read();
		if(r==-1)
			throw new IOException();
		a = (byte)r;
		r = is.read();
		if(r==-1)
			throw new IOException();
		
		if((a&0x40)==0x00)
		{
			r = is.read();
			if(r==-1)
				throw new IOException();
			len = (int)(0xFF00&(r<<8));
			r = is.read();
			if(r==-1)
				throw new IOException();
			len |= r;
		}
		else
		{
			r = is.read();
			if(r==-1)
				throw new IOException();
			len = (int)(0xFF0000&(r<<16));
			r = is.read();
			if(r==-1)
				throw new IOException();
			len += (int)(0xFF00&(r<<8));;			
			r = is.read();
			if(r==-1)
				throw new IOException();
			len |= r;			
		}
		
		byte[] pkt = new byte[len];
		for(int i=0;i<len;i++)
		{
			r = is.read();
			if(r==-1)
				throw new IOException();
			pkt[i] = (byte)r;
		}
		return pkt;
	}

	protected void sWrite(byte[] buffer) throws IOException
	{
		try{
			byte[] tmp = null;
			if(buffer.length>0xff)
			{
				tmp = new byte[buffer.length+5];
				tmp[0] = 0x41;
				tmp[1] = 0x00;
				tmp[2] = (byte)((buffer.length&0xFF0000)>>16);
				tmp[3] = (byte)((buffer.length&0x00FF00)>>8);
				tmp[4] = (byte)(buffer.length&0x0000FF);
				System.arraycopy(buffer, 0, tmp, 5, buffer.length);
			}
			else
			{
				tmp = new byte[buffer.length+4];
				tmp[0] = 0x01;
				tmp[1] = 0x00;
				tmp[2] = (byte)((buffer.length&0xFF00)>>8);
				tmp[3] = (byte)(buffer.length&0x00FF);
				System.arraycopy(buffer, 0, tmp, 4, buffer.length);
			}
			buffer = tmp;
			os.write(buffer);
			os.flush();
		}catch(Exception e){
			throw new IOException();
		}		
	}

	@Override
	public ResponseAPDU transmit(CommandAPDU apdu) throws CardException {
		try {
			sWrite(apdu.getBytes());
			byte[] apduR = sRead();
			return new ResponseAPDU(apduR);
		} catch (IOException e) {
			throw new CardException(e);
		}
	}

	@Override
	public int transmit(ByteBuffer arg0, ByteBuffer arg1) throws CardException {
		if(arg0.hasArray())
		{
			byte[] apduC = arg0.array();
			arg1.put(apduC);
			return apduC.length;
		}
		
		return 0;
	}

	public byte[] transmitBytes(byte[] apdu) throws CardException {
		try {
			sWrite(apdu);
			byte[] apduR = sRead();
			return apduR;
		} catch (IOException e) {
			throw new CardException(e);
		}
	}

}
