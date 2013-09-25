package net.sourceforge.gpj.jcremoteterminal;

import java.net.Socket;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;

public class CloudTerminal extends CardTerminal {

	private String ip = null;
	private int port = 0;
	private Socket socket = null;
	
	public CloudTerminal(String ip, int port)
	{
		this.ip = ip;
		this.port = port;
	}

	@Override
	public Card connect(String arg0) throws CardException {
		try{
			socket = new Socket(ip, port);
			socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            return new CloudCard(socket);
		} catch (Exception e){
			CardException ce = new CardException("SCARD_E_NO_SMARTCARD");	
			ce.initCause(new Throwable("SCARD_E_NO_SMARTCARD"));
			throw ce;
		}
	}

	@Override
	public String getName() {
		return "SimplyTapp";
	}

	@Override
	public boolean isCardPresent() throws CardException {
		if(socket!=null && socket.isConnected())
			return true;
		return false;
	}

	@Override
	public boolean waitForCardAbsent(long arg0) throws CardException {
		long time = System.currentTimeMillis();
		while((System.currentTimeMillis()-time)<arg0)
		{
			if(socket==null)
				throw new CardException("");
			if(!socket.isConnected())
				return true;
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				throw new CardException("");
			}
		}
		return false;
	}

	@Override
	public boolean waitForCardPresent(long arg0) throws CardException {
		long time = System.currentTimeMillis();
		while((System.currentTimeMillis()-time)<arg0)
		{
			if(socket==null)
				throw new CardException("");
			if(socket.isConnected())
				return true;
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				throw new CardException("");
			}
		}
		return false;
	}

}
