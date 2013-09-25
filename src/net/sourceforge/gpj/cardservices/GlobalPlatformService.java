/*
 * gpj - Global Platform for Java SmartCardIO
 *
 * Copyright (C) 2009 Wojciech Mostowski, woj@cs.ru.nl
 * Copyright (C) 2009 Francois Kooman, F.Kooman@student.science.ru.nl
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

package net.sourceforge.gpj.cardservices;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.smartcardio.*;

import net.sourceforge.gpj.cardservices.ciphers.ICipher;

import net.sourceforge.gpj.cardservices.exceptions.*;
import net.sourceforge.gpj.jcremoteterminal.CloudTerminal;

/**
 * The main Global Platform Service class. Provides most of the Global Platform
 * functionality and a simple command line application (see the main method) for
 * managing GP compliant smart cards.
 * 
 */
public class GlobalPlatformService implements ISO7816, APDUListener {

	public static final int SCP_ANY = 0;

    public static final int SCP_01_05 = 1;

    public static final int SCP_01_15 = 2;

    public static final int SCP_02_04 = 3;

    public static final int SCP_02_05 = 4;

    public static final int SCP_02_0A = 5;

    public static final int SCP_02_0B = 6;

    public static final int SCP_02_14 = 7;

    public static final int SCP_02_15 = 8;

    public static final int SCP_02_1A = 9;

    public static final int SCP_02_1B = 10;

    public static final int APDU_CLR = 0x00;

    public static final int APDU_MAC = 0x01;

    public static final int APDU_ENC = 0x02;

    public static final int APDU_RMAC = 0x10;

    public static final int DIVER_NONE = 0;

    public static final int DIVER_VISA2 = 1;

    public static final int DIVER_EMV = 2;
    
    public static final byte CLA_GP = (byte) 0x80;

    public static final byte CLA_MAC = (byte) 0x84;

    public static final byte INIT_UPDATE = (byte) 0x50;

    public static final byte EXT_AUTH = (byte) 0x82;

    public static final byte GET_DATA = (byte) 0xCA;

    public static final byte INSTALL = (byte) 0xE6;

    public static final byte LOAD = (byte) 0xE8;

    public static final byte DELETE = (byte) 0xE4;

    public static final byte GET_STATUS = (byte) 0xF2;

    protected AID sdAID = null;

    public static final byte[] defaultEncKey = { 0x40, 0x41, 0x42, 0x43, 0x44,
            0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F };

    public static final byte[] defaultMacKey = { 0x40, 0x41, 0x42, 0x43, 0x44,
            0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F };

    public static final byte[] defaultKekKey = { 0x40, 0x41, 0x42, 0x43, 0x44,
            0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F };

    public static Map<String, byte[]> SPECIAL_MOTHER_KEYS = new TreeMap<String, byte[]>();

    static {
    	SPECIAL_MOTHER_KEYS.put(AID.GEMALTO, new byte[] {0x47, 0x45, 0x4D, 0x58, 0x50, 0x52, 0x45, 0x53, 0x53, 0x4F, 0x53, 0x41, 0x4D, 0x50, 0x4C, 0x45});
    }

    public static final int defaultLoadSize = 255;

    protected SecureChannelWrapper wrapper = null;

    protected CardChannel channel = null;

    protected int scpVersion = SCP_ANY;

    private HashMap<Integer, KeySet> keys = new HashMap<Integer, KeySet>();

    private ArrayList<APDUListener> apduListeners = new ArrayList<APDUListener>();
    
    private String path = "";

    @SuppressWarnings("rawtypes")
	private HashMap helpMap;

    /**
     * Set the security domain AID, the channel and use scpAny.
     * 
     * @param aid
     *            applet identifier of the security domain
     * @param channel
     *            channel to talk to
     * @throws IllegalArgumentException
     *             if {@code channel} is null.
     */
    public GlobalPlatformService(AID aid, CardChannel channel)
            throws IllegalArgumentException {
        this(aid, channel, SCP_ANY);
    }

    /**
     * Full constructor, setting the security domain AID, the channel and the
     * scp version.
     * 
     * @param aid
     *            applet identifier of the security domain
     * @param channel
     *            channel to talk to
     * @param scpVersion
     * @throws IllegalArgumentException
     *             if {@code scpVersion} is out of range or {@code channel} is
     *             null.
     */
    public GlobalPlatformService(AID aid, CardChannel channel, int scpVersion)
            throws IllegalArgumentException {
        this(channel, scpVersion);
        this.sdAID = aid;
    }

    /**
     * Set the channel and use the default security domain AID and scpAny.
     * 
     * @param channel
     *            channel to talk to
     * @throws IllegalArgumentException
     *             if {@code channel} is null.
     */
    public GlobalPlatformService(CardChannel channel)
            throws IllegalArgumentException {
        this(channel, SCP_ANY);
    }

    /**
     * Set the channel and the scpVersion and use the default security domain
     * AID.
     * 
     * @param channel
     *            channel to talk to
     * @param scpVersion
     * @throws IllegalArgumentException
     *             if {@code scpVersion} is out of range or {@code channel} is
     *             null.
     */
    public GlobalPlatformService(CardChannel channel, int scpVersion)
            throws IllegalArgumentException {
        if (scpVersion != SCP_ANY && scpVersion != SCP_02_0A
                && scpVersion != SCP_02_0B && scpVersion != SCP_02_1A
                && scpVersion != SCP_02_1B) {
            throw new IllegalArgumentException(
                    "Only implicit secure channels can be set through the constructor.");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        this.channel = channel;
        this.scpVersion = scpVersion;
        putHelpMap();
    }

    public void addAPDUListener(APDUListener l) {
        apduListeners.add(l);
    }

    public void removeAPDUListener(APDUListener l) {
        apduListeners.remove(l);
    }

    public void notifyExchangedAPDU(CommandAPDU c, ResponseAPDU r, int ms) {
        for (APDUListener l : apduListeners) {
            l.exchangedAPDU(c, r);
            GPUtil
    				.debug("("+ms+" ms)");
        }
    }

    public void exchangedAPDU(CommandAPDU c, ResponseAPDU r) {
        GPUtil
                .debug("Command  APDU: "
                        + GPUtil.byteArrayToString(c.getBytes()));
        GPUtil
                .debug("Response APDU: "
                        + GPUtil.byteArrayToString(r.getBytes()));
    }

    /**
     * Establish a connection to the security domain specified in the
     * constructor. This method is required before doing
     * {@link #openSecureChannel openSecureChannel}.
     * 
     * @throws GPSecurityDomainSelectionException
     *             if security domain selection fails for some reason
     * @throws CardException
     *             on data transmission errors
     */
    public void open() throws GPSecurityDomainSelectionException, CardException {
    	
    	if (sdAID == null) {
    		// Try known SD AIDs
    		short sw = 0;
    		for(Map.Entry<String,AID> entry : AID.SD_AIDS.entrySet()) {
        		CommandAPDU command = new CommandAPDU(CLA_ISO7816, INS_SELECT, 0x04,
                        0x00, entry.getValue().getBytes());
        		long t = System.currentTimeMillis();
                ResponseAPDU resp = channel.transmit(command);
                notifyExchangedAPDU(command, resp,(int)(System.currentTimeMillis()-t));
                sw = (short) resp.getSW();
                if (sw == SW_NO_ERROR) {
                	sdAID = entry.getValue();
                    System.out.println("Successfully selected Security Domain "+entry.getKey()+" "+
                            entry.getValue().toString());
                	break;
                }
                System.out.println("Failed to select Security Domain "+entry.getKey()+" "+
                  entry.getValue().toString()+", SW: "+GPUtil.swToString(sw));
    		}
    		if(sdAID == null) {
        		throw new GPSecurityDomainSelectionException(sw,
                        "Could not select any of the known Security Domains!");
    			
    		}
    	} else {
    		CommandAPDU command = new CommandAPDU(CLA_ISO7816, INS_SELECT, 0x04,
                    0x00, sdAID.getBytes());
            long t = System.currentTimeMillis();
            ResponseAPDU resp = channel.transmit(command);
            notifyExchangedAPDU(command, resp, (int)(System.currentTimeMillis()-t));
            short sw = (short) resp.getSW();
            if (sw != SW_NO_ERROR) {
                throw new GPSecurityDomainSelectionException(sw,
                        "Could not select custom sdAID " + sdAID + ", SW: "
                                + GPUtil.swToString(sw));
            }	
    	}	
    }

    /**
     * Establishes a secure channel to the security domain. The security domain
     * must have been selected with {@link open open} before. The {@code keySet}
     * must have been initialized with {@link setKeys setKeys} before.
     * 
     * @throws IllegalArgumentException
     *             if the arguments are out of range or the keyset is undefined
     * @throws CardException
     *             if some communication problem is encountered.
     */
    public void openSecureChannel(int keySet, int keyId, int scpVersion,
            int securityLevel, boolean gemalto) throws IllegalArgumentException, CardException {

        if (scpVersion < SCP_ANY || scpVersion > SCP_02_1B) {
            throw new IllegalArgumentException("Invalid SCP version.");
        }

        if (scpVersion == SCP_02_0A || scpVersion == SCP_02_0B
                || scpVersion == SCP_02_1A || scpVersion == SCP_02_1B) {
            throw new IllegalArgumentException(
                    "Implicit secure channels cannot be initialized explicitly (use the constructor).");
        }

        if (keySet < 0 || keySet > 127) {
            throw new IllegalArgumentException("Wrong key set.");
        }

        int mask = ~(APDU_MAC | APDU_ENC | APDU_RMAC);

        if ((securityLevel & mask) != 0) {
            throw new IllegalArgumentException(
                    "Wrong security level specification");
        }
        if ((securityLevel & APDU_ENC) != 0) {
            securityLevel |= APDU_MAC;
        }

        KeySet staticKeys = keys.get(new Integer(keySet));
        if (staticKeys == null) {
            throw new IllegalArgumentException("Key set " + keySet
                    + " not defined.");
        }

        if(gemalto && AID.SD_AIDS.get(AID.GEMALTO).equals(sdAID)) {            
        	// get data, prepare diver buffer
            CommandAPDU c = new CommandAPDU(CLA_GP, INS_GET_DATA, 0x9F ,0x7F, 256);
            //byte[] cData=new byte[]{(byte)CLA_GP,(byte)INS_GET_DATA,(byte)0x9F,(byte)0x7F,0x00};
            //CommandAPDU c = new CommandAPDU(cData);
            long ti = System.currentTimeMillis();
            ResponseAPDU r = channel.transmit(c);
            notifyExchangedAPDU(c, r, (int)(System.currentTimeMillis()-ti));
            short sw = (short) r.getSW();
            if (sw != SW_NO_ERROR) {
                throw new CardException("Wrong "+AID.GEMALTO+" get CPLC data, SW: " + GPUtil.swToString(sw));
            }
        	byte[] diverData = new byte[16];
            byte[] t = sdAID.getBytes();
            diverData[0] = t[t.length - 2];
            diverData[1] = t[t.length - 1];
            System.arraycopy(r.getData(), 15, diverData, 4, 4);
            
        	staticKeys.diversify(diverData);
        }
        
        byte[] rand = new byte[8];
        new Random().nextBytes(rand);

        CommandAPDU initUpdate = new CommandAPDU(CLA_GP, INIT_UPDATE, keySet,
                keyId, rand);

        long t = System.currentTimeMillis();
        ResponseAPDU response = channel.transmit(initUpdate);
        notifyExchangedAPDU(initUpdate, response, (int)(System.currentTimeMillis()-t));
        short sw = (short) response.getSW();
        if (sw != SW_NO_ERROR) {
            throw new CardException("Wrong initialize update, SW: "
                    + GPUtil.swToString(sw));
        }
        byte[] result = response.getData();
        if (result.length != 28) {
            throw new CardException("Wrong initialize update response length.");
        }
        if (scpVersion == SCP_ANY) {
            scpVersion = result[11] == 2 ? SCP_02_15 : SCP_01_05;
        }
        int scp = (scpVersion < SCP_02_04) ? 1 : 2;
        if (scp != result[11]) {
            throw new CardException("Secure Channel Protocol version mismatch.");
        }
        if (scp == 1 && ((scpVersion & APDU_RMAC) != 0)) {
            scpVersion &= ~APDU_RMAC;
        }

        // Only diversify default key sets
        if (keySet == 0 || keySet == 255) {
            staticKeys.diversify(result);
        }

        if (keySet > 0 && result[10] != (byte) keySet) {
            throw new CardException("Key set mismatch.");
        } else {
            keySet = result[10] & 0xff;
        }

        KeySet sessionKeys = null;

        if (scp == 1) {
            sessionKeys = deriveSessionKeysSCP01(staticKeys, rand, result);
        } else {
            sessionKeys = deriveSessionKeysSCP02(staticKeys, result[12],
                    result[13], false);
        }

        ByteArrayOutputStream bo = new ByteArrayOutputStream();

        try {
            bo.write(rand);
            bo.write(result, 12, 8);
        } catch (IOException ioe) {

        }

        byte[] myCryptogram = GPUtil.mac_3des(sessionKeys.keys[0], GPUtil
                .pad80(bo.toByteArray()), new byte[8]);
        byte[] cardCryptogram = new byte[8];
        System.arraycopy(result, 20, cardCryptogram, 0, 8);
        if (!Arrays.equals(cardCryptogram, myCryptogram)) {
            throw new CardException("Card cryptogram invalid.");
        }

        try {
            bo.reset();
            bo.write(result, 12, 8);
            bo.write(rand);
        } catch (IOException ioe) {

        }

        byte[] authData = GPUtil.mac_3des(sessionKeys.keys[0], GPUtil.pad80(bo
                .toByteArray()), new byte[8]);

        wrapper = new SecureChannelWrapper(sessionKeys, scpVersion, APDU_MAC,
                null, null);
        CommandAPDU externalAuthenticate = new CommandAPDU(CLA_MAC, EXT_AUTH,
                securityLevel, 0, authData);
        response = transmit(externalAuthenticate);
        sw = (short) response.getSW();
        if (sw != SW_NO_ERROR) {
            throw new CardException("External authenticate failed. SW: "
                    + GPUtil.swToString(sw));
        }
        wrapper.setSecurityLevel(securityLevel);
        if ((securityLevel & APDU_RMAC) != 0) {
            wrapper.ricv = new byte[8];
            System.arraycopy(wrapper.icv, 0, wrapper.ricv, 0, 8);
        }
        this.scpVersion = scpVersion;
    }

    /**
     * Convenience method combining {@link #open open()} and
     * {@link #openSecureChannel openSecureChannel} with the default keys and no
     * diversification.
     * 
     * @throws CardException
     *             when communication problems with the card or the selected
     *             security domain arise.
     */
    public void openWithDefaultKeys() throws CardException {
        open();
        int keySet = 0;
        setKeys(keySet, defaultEncKey, defaultMacKey, defaultKekKey);
        openSecureChannel(keySet, 0, SCP_ANY, APDU_MAC, false);
    }

    public boolean isSecureChannelOpen() {
        return wrapper != null;
    }

    private KeySet deriveSessionKeysSCP01(KeySet staticKeys, byte[] hostRandom,
            byte[] cardResponse) throws CardException {
        byte[] derivationData = new byte[16];

        System.arraycopy(cardResponse, 16, derivationData, 0, 4);
        System.arraycopy(hostRandom, 0, derivationData, 4, 4);
        System.arraycopy(cardResponse, 12, derivationData, 8, 4);
        System.arraycopy(hostRandom, 4, derivationData, 12, 4);
        KeySet sessionKeys = new KeySet();

        try {
            ICipher cipher = ICipher.Factory
                    .getImplementation(ICipher.DESEDE_ECB_NOPADDING);

            for (int keyIndex = 0; keyIndex < 2; keyIndex++) {
                cipher.setKey(GPUtil.getKey(staticKeys.keys[keyIndex], 24));
                sessionKeys.keys[keyIndex] = cipher.encrypt(derivationData);
            }
        } catch (Exception e) {
            throw new CardException("Session key derivation failed.", e);
        }
        sessionKeys.keys[2] = staticKeys.keys[2];
        return sessionKeys;
    }

    private KeySet deriveSessionKeysSCP02(KeySet staticKeys, byte seq1,
            byte seq2, boolean implicitChannel) throws CardException {
        KeySet sessionKeys = new KeySet();

        try {
            byte[] derivationData = new byte[16];
            derivationData[2] = seq1;
            derivationData[3] = seq2;

            byte[] constantMAC = new byte[] { (byte) 0x01, (byte) 0x01 };
            System.arraycopy(constantMAC, 0, derivationData, 0, 2);

            ICipher cipher = ICipher.Factory.getImplementation(
                    ICipher.DESEDE_CBC_NOPADDING, GPUtil.getKey(
                            staticKeys.keys[1], 24), new byte[8]);
            sessionKeys.keys[1] = cipher.encrypt(derivationData);

            // TODO: is this correct?
            if (implicitChannel) {
                if (seq2 == (byte) 0xff) {
                    seq2 = (byte) 0;
                    seq1++;
                } else {
                    seq2++;
                }
                derivationData[2] = seq1;
                derivationData[3] = seq2;
            }

            byte[] constantRMAC = new byte[] { (byte) 0x01, (byte) 0x02 };
            System.arraycopy(constantRMAC, 0, derivationData, 0, 2);

            cipher.setKey(GPUtil.getKey(staticKeys.keys[1], 24));
            sessionKeys.keys[3] = cipher.encrypt(derivationData);

            byte[] constantENC = new byte[] { (byte) 0x01, (byte) 0x82 };
            System.arraycopy(constantENC, 0, derivationData, 0, 2);

            cipher.setKey(GPUtil.getKey(staticKeys.keys[0], 24));
            sessionKeys.keys[0] = cipher.encrypt(derivationData);

            byte[] constantDEK = new byte[] { (byte) 0x01, (byte) 0x81 };
            System.arraycopy(constantDEK, 0, derivationData, 0, 2);
            cipher.setKey(GPUtil.getKey(staticKeys.keys[2], 24));
            sessionKeys.keys[2] = cipher.encrypt(derivationData);
            sessionKeys.type = "DES_ECB";
        } catch (Exception e) {
            throw new CardException("Key derivation failed.", e);
        }
        return sessionKeys;

    }

    public ResponseAPDU transmit(CommandAPDU command)
            throws IllegalStateException, CardException {
        if (wrapper == null
                && (scpVersion == SCP_02_0A || scpVersion == SCP_02_0B
                        || scpVersion == SCP_02_1A || scpVersion == SCP_02_1B)) {
            CommandAPDU getData = new CommandAPDU(CLA_GP, GET_DATA, 0, 0xE0);
            long t = System.currentTimeMillis();
            ResponseAPDU data = channel.transmit(getData);
            notifyExchangedAPDU(getData, data, (int)(System.currentTimeMillis()-t));

            byte[] result = data.getBytes();
            int keySet = 0;
            if (result.length > 6)
                keySet = result[result[0] != 0 ? 5 : 6];

            KeySet staticKeys = keys.get(keySet);
            if (staticKeys == null) {
                throw new IllegalStateException("Key set " + keySet
                        + " not defined.");
            }

            CommandAPDU getSeq = new CommandAPDU(CLA_GP, GET_DATA, 0, 0xC1);
            t = System.currentTimeMillis();
            ResponseAPDU seq = channel.transmit(getSeq);
            notifyExchangedAPDU(getSeq, seq, (int)(System.currentTimeMillis()-t));
            result = seq.getBytes();
            short sw = (short) seq.getSW();
            if (sw != SW_NO_ERROR) {
                throw new CardException("Reading sequence counter failed. SW: "
                        + GPUtil.swToString(sw));
            }

            try {
                KeySet sessionKeys = deriveSessionKeysSCP02(staticKeys,
                        result[2], result[3], true);
                byte[] temp = GPUtil.pad80(sdAID.getBytes());

                byte[] icv = GPUtil.mac_des_3des(sessionKeys.keys[1], temp,
                        new byte[8]);
                byte[] ricv = GPUtil.mac_des_3des(sessionKeys.keys[3], temp,
                        new byte[8]);
                wrapper = new SecureChannelWrapper(sessionKeys, scpVersion,
                        APDU_MAC, icv, ricv);
            } catch (Exception e) {
                throw new CardException(
                        "Implicit secure channel initialization failed.", e);
            }
        }
        CommandAPDU wc = command;
        if(wrapper!=null)
        	wc = wrapper.wrap(command);
        long t = System.currentTimeMillis();
        ResponseAPDU wr = channel.transmit(wc);
        notifyExchangedAPDU(wc, wr, (int)(System.currentTimeMillis()-t));
        if(wrapper!=null)
        	return wrapper.unwrap(wr);
        else
        	return wr;
    }

    public void setKeys(int index, byte[] encKey, byte[] macKey, byte[] kekKey,
            int diversification) {
    	keys.put(index, new KeySet("DES_ECB",encKey, macKey, kekKey, diversification));
    }

    public void setKeys(int index, byte[] encKey, byte[] macKey, byte[] kekKey) {
        setKeys(index, encKey, macKey, kekKey, DIVER_NONE);
    }

    public void setKeys(int index, String type, byte[] encKey, byte[] macKey, byte[] kekKey) {
    	keys.put(index, new KeySet(type, encKey, macKey, kekKey));
    }
    
    public void setKey(int index, int id, String type, byte[] key) {
    	if(keys.get(index)!=null)
    	{
    		KeySet keySet = keys.get(index);
    		keySet.setKey(id, type, key);
    	}
    	else
    	{
    		KeySet keySet = new KeySet();
    		keySet.setKey(id, type, key);
    		keys.put(index, keySet);
    	}
    }
    

    /**
     * 
     * Convenience method, opens {@code fileName} and calls then
     * {@link #loadCapFile(CapFile, boolean, boolean, int, boolean, boolean)}
     * with otherwise unmodified parameters.
     * 
     * @param fileName
     *            file name of the applet cap file
     * @param includeDebug
     * @param separateComponents
     * @param blockSize
     * @param loadParam
     * @param useHash
     * @throws GPInstallForLoadException
     *             if the install-for-load command fails with a non 9000
     *             response status
     * @throws GPLoadException
     *             if one of the cap file APDU's fails with a non 9000 response
     *             status
     * @throws CardException
     *             for low-level communication problems
     * @throws IOException
     *             if opening {@code fileName} fails
     */
    public void loadCapFile(URL url, boolean includeDebug,
            boolean separateComponents, int blockSize, boolean loadParam,
            boolean useHash) throws IOException, GPInstallForLoadException,
            GPLoadException, CardException {
        CapFile cap = null;
        cap = new CapFile(url.openStream(), null);
        loadCapFile(cap, includeDebug, separateComponents, blockSize,
                loadParam, useHash);
    }

    /**
     * 
     * 
     * 
     * @param cap
     * @param includeDebug
     * @param separateComponents
     * @param blockSize
     * @param loadParam
     * @param useHash
     * @throws GPInstallForLoadException
     *             if the install-for-load command fails with a non 9000
     *             response status
     * @throws GPLoadException
     *             if one of the cap file APDU's fails with a non 9000 response
     *             status
     * @throws CardException
     *             for low-level communication problems
     */
    public void loadCapFile(CapFile cap, boolean includeDebug,
            boolean separateComponents, int blockSize, boolean loadParam,
            boolean useHash) throws GPInstallForLoadException, GPLoadException,
            CardException {

        byte[] hash = useHash ? cap.getLoadFileDataHash(includeDebug)
                : new byte[0];
        int len = cap.getCodeLength(includeDebug);
        byte[] loadParams = loadParam ? new byte[] { (byte) 0xEF, 0x04,
                (byte) 0xC6, 0x02, (byte) ((len & 0xFF00) >> 8),
                (byte) (len & 0xFF) } : new byte[0];

        ByteArrayOutputStream bo = new ByteArrayOutputStream();

        try {
            bo.write(cap.getPackageAID().getLength());
            bo.write(cap.getPackageAID().getBytes());

            bo.write(sdAID.getLength());
            bo.write(sdAID.getBytes());

            bo.write(hash.length);
            bo.write(hash);

            bo.write(loadParams.length);
            bo.write(loadParams);
            bo.write(0);
        } catch (IOException ioe) {

        }
        CommandAPDU installForLoad = new CommandAPDU(CLA_GP, INSTALL, 0x02,
                0x00, bo.toByteArray());
        ResponseAPDU response = transmit(installForLoad);
        short sw = (short) response.getSW();
        if (sw != SW_NO_ERROR) {
            throw new GPInstallForLoadException(sw,
                    "Install for Load failed, SW: " + GPUtil.swToString(sw));
        }
        List<byte[]> blocks = cap.getLoadBlocks(includeDebug,
                separateComponents, blockSize);
        for (int i = 0; i < blocks.size(); i++) {
            CommandAPDU load = new CommandAPDU(CLA_GP, LOAD, (i == blocks
                    .size() - 1) ? 0x80 : 0x00, (byte) i, blocks.get(i));
            //t = System.currentTimeMillis();
            response = transmit(load);
            //notifyExchangedAPDU(load, response, (int)(System.currentTimeMillis()-t));
            sw = (short) response.getSW();
            if (sw != SW_NO_ERROR) {
                throw new GPLoadException(sw, "Load failed, SW: "
                        + GPUtil.swToString(sw));
            }

        }
    }

    /**
     * Install an applet and make it selectable. The package and applet AID must
     * be present (ie. non-null). If one of the other parameters is null
     * sensible defaults are chosen. If installation parameters are used, they
     * must be passed in a special format, see parameter description below.
     * <P>
     * Before installation the package containing the applet must be loaded onto
     * the card, see {@link #loadCapFile loadCapFile}.
     * <P>
     * This method installs just one applet. Call it several times for packages
     * containing several applets.
     * 
     * @param packageAID
     *            the package that containing the applet
     * @param appletAID
     *            the applet to be installed
     * @param instanceAID
     *            the applet AID passed to the install method of the applet,
     *            defaults to {@code packageAID} if null
     * @param privileges
     *            privileges encoded as byte
     * @param installParams
     *            tagged installation parameters, defaults to {@code 0xC9 00}
     *            (ie. no installation parameters) if null, if non-null the
     *            format is {@code 0xC9 len data...}
     * @throws GPMakeSelectableException
     *             if the command install for install and make selectable fails
     * @throws CardException
     *             for data transmission errors
     * @throws NullPointerException
     *             if either packageAID or appletAID is null
     */
    public void installAndMakeSelecatable(AID packageAID, AID appletAID,
            AID instanceAID, byte privileges, byte[] installParams,
            byte[] installToken) throws GPMakeSelectableException,
            CardException {
        if (installParams == null) {
            installParams = new byte[] { (byte) 0xC9, 0x00 };
        }
        if (instanceAID == null) {
            instanceAID = appletAID;
        }
        if (installToken == null) {
            installToken = new byte[0];
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            bo.write(packageAID.getLength());
            bo.write(packageAID.getBytes());

            bo.write(appletAID.getLength());
            bo.write(appletAID.getBytes());

            bo.write(instanceAID.getLength());
            bo.write(instanceAID.getBytes());

            bo.write(1);
            bo.write(privileges);
            bo.write(installParams.length);
            bo.write(installParams);

            bo.write(installToken.length);
            bo.write(installToken);
        } catch (IOException ioe) {

        }
        CommandAPDU install = new CommandAPDU(CLA_GP, INSTALL, 0x0C, 0x00, bo
                .toByteArray());
        ResponseAPDU response = transmit(install);
        short sw = (short) response.getSW();
        if (sw != SW_NO_ERROR) {
            throw new GPMakeSelectableException(sw,
                    "Install for Install and make selectable failed, SW: "
                            + GPUtil.swToString(sw));
        }

    }

    /**
     * Delete file {@code aid} on the card. Delete dependencies as well if
     * {@code deleteDeps} is true.
     * 
     * @param aid
     *            identifier of the file to delete
     * @param deleteDeps
     *            if true delete dependencies as well
     * @throws GPDeleteException
     *             if the delete command fails with a non 9000 response status
     * @throws CardException
     *             for low-level communication errors
     */
    public void deleteAID(AID aid, boolean deleteDeps)
            throws GPDeleteException, CardException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            bo.write(0x4f);
            bo.write(aid.getLength());
            bo.write(aid.getBytes());
        } catch (IOException ioe) {

        }
        CommandAPDU delete = new CommandAPDU(CLA_GP, DELETE, 0x00,
                deleteDeps ? 0x80 : 0x00, bo.toByteArray());
        ResponseAPDU response = transmit(delete);
        short sw = (short) response.getSW();
        if (sw != SW_NO_ERROR) {
            throw new GPDeleteException(sw, "Deletion failed, SW: "
                    + GPUtil.swToString(sw));
        }
    }

    /**
     * Get card status. Perform all possible variants of the get status command
     * and return all entries reported by the card in an AIDRegistry.
     * 
     * @return registry with all entries on the card
     * @throws CardException
     *             in case of communication errors
     */
    public AIDRegistry getStatus() throws CardException {
        AIDRegistry registry = new AIDRegistry();
        int[] p1s = { 0x80, 0x40 };
        for (int p1 : p1s) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            CommandAPDU getStatus = new CommandAPDU(CLA_GP, GET_STATUS, p1,
                    0x00, new byte[] { 0x4F, 0x00 });
            ResponseAPDU response = transmit(getStatus);
            short sw = (short) response.getSW();
            if (sw != SW_NO_ERROR && sw != (short) 0x6310) {
                continue;
            }
            try {
                bo.write(response.getData());
            } catch (IOException ioe) {

            }
            while (response.getSW() == 0x6310) {
                getStatus = new CommandAPDU(CLA_GP, GET_STATUS, p1, 0x01,
                        new byte[] { 0x4F, 0x00 });
                response = transmit(getStatus);
                try {
                    bo.write(response.getData());
                } catch (IOException ioe) {

                }
                sw = (short) response.getSW();
                if (sw != SW_NO_ERROR && sw != (short) 0x6310) {
                    throw new CardException("Get Status failed, SW: "
                            + GPUtil.swToString(sw));
                }
            }
            // parse data no sub-AID
            int index = 0;
            byte[] data = bo.toByteArray();
            while (index < data.length) {
                int len = data[index++];
                AID aid = new AID(data, index, len);
                index += len;
                int life_cycle = data[index++];
                int privileges = data[index++];

                AIDRegistryEntry.Kind kind = AIDRegistryEntry.Kind.IssuerSecurityDomain;
                if (p1 == 0x40) {
                    if ((privileges & 0x80) == 0)
                        kind = AIDRegistryEntry.Kind.Application;
                    else
                        kind = AIDRegistryEntry.Kind.SecurityDomain;
                }

                AIDRegistryEntry entry = new AIDRegistryEntry(aid, life_cycle,
                        privileges, kind);
                registry.add(entry);
            }
        }
        p1s = new int[] { 0x10, 0x20 };
        boolean succ10 = false;
        for (int p1 : p1s) {
            if (succ10)
                continue;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            CommandAPDU getStatus = new CommandAPDU(CLA_GP, GET_STATUS, p1,
                    0x00, new byte[] { 0x4F, 0x00 });
            ResponseAPDU response = transmit(getStatus);
            short sw = (short) response.getSW();
            if (sw != SW_NO_ERROR && sw != (short) 0x6310) {
                continue;
            }
            if (p1 == 0x10)
                succ10 = true;
            // copy data
            try {
                bo.write(response.getData());
            } catch (IOException ioe) {

            }

            while (response.getSW() == 0x6310) {
                getStatus = new CommandAPDU(CLA_GP, GET_STATUS, p1, 0x01,
                        new byte[] { 0x4F, 0x00 });
                response = transmit(getStatus);
                try {
                    bo.write(response.getData());
                } catch (IOException ioe) {

                }
                sw = (short) response.getSW();
                if (sw != SW_NO_ERROR && sw != (short) 0x6310) {
                    throw new CardException("Get Status failed, SW: "
                            + GPUtil.swToString(sw));
                }
            }

            int index = 0;
            byte[] data = bo.toByteArray();
            while (index < data.length) {
                int len = data[index++];
                AID aid = new AID(data, index, len);
                index += len;
                AIDRegistryEntry entry = new AIDRegistryEntry(
                        aid,
                        data[index++],
                        data[index++],
                        p1 == 0x10 ? AIDRegistryEntry.Kind.ExecutableLoadFilesAndModules
                                : AIDRegistryEntry.Kind.ExecutableLoadFiles);
                if (p1 == 0x10) {
                    int num = data[index++];
                    for (int i = 0; i < num; i++) {
                        len = data[index++];
                        aid = new AID(data, index, len);
                        index += len;
                        entry.addExecutableAID(aid);
                    }
                }
                registry.add(entry);
            }
        }
        return registry;
    }
    
    private class KeySet {

    	private int diversification = DIVER_NONE;

        private boolean diversified = false;

        private byte[][] keys = null;
        
        private String type = null;
        
        private void setKey(int id, byte[] key){
        	if(id==1)
        		keys[0] = key;
        	else if(id==2)
        		keys[1] = key;
        	else if(id==3)
        		keys[2] = key;
        }
        
        private void setKey(int id, String type, byte[] key){
        	if(id==1)
        		keys[0] = key;
        	else if(id==2)
        		keys[1] = key;
        	else if(id==3)
        		keys[2] = key;
        	this.type = type;
        }
        
        private KeySet() {
            keys = new byte[][] { null, null, null, null };
            type = null;
        }

        private KeySet(String type, byte[] encKey, byte[] macKey, byte[] kekKey) {
            keys = new byte[][] { encKey, macKey, kekKey };
            this.type = type;
        }

        
        private KeySet(byte[] encKey, byte[] macKey, byte[] kekKey) {
            keys = new byte[][] { encKey, macKey, kekKey };
        }

        private KeySet(byte[] masterKey) { this(masterKey, masterKey, masterKey); }
          
        private KeySet(byte[] masterKey, int diversification) {
          this(masterKey, masterKey, masterKey, diversification); }
        

        private KeySet(byte[] encKey, byte[] macKey, byte[] kekKey,
                int diversification) {
            this(encKey, macKey, kekKey);
            this.diversification = diversification;
        }

        private KeySet(String type, byte[] encKey, byte[] macKey, byte[] kekKey,
                int diversification) {
            this(encKey, macKey, kekKey);
            this.diversification = diversification;
            this.type = type;
        }

        private void diversify(byte[] diverData) throws CardException {
            if (diversified || diversification == DIVER_NONE) {
                return;
            }
            try {
                ICipher cipher = ICipher.Factory
                        .getImplementation(ICipher.DESEDE_ECB_NOPADDING);
                byte[] data = new byte[16];
                for (int i = 0; i < 3; i++) {
                    fillData(data, diverData, i + 1);
                    cipher.setKey(GPUtil.getKey(keys[i], 24));
                    keys[i] = cipher.encrypt(data);
                }
                diversified = true;
            } catch (Exception e) {
                diversified = false;
                throw new CardException("Diversification failed.", e);
            }
        }

        private void fillData(byte[] data, byte[] res, int i)
                throws CardException {
            if (diversification == DIVER_VISA2) {
                // This is VISA2
                data[0] = res[0];
                data[1] = res[1];
                data[2] = res[4];
                data[3] = res[5];
                data[4] = res[6];
                data[5] = res[7];
                data[6] = (byte) 0xF0;
                data[7] = (byte) i;
                data[8] = res[0];
                data[9] = res[1];
                data[10] = res[4];
                data[11] = res[5];
                data[12] = res[6];
                data[13] = res[7];
                data[14] = (byte) 0x0F;
                data[15] = (byte) i;
            } else {
                // This is EMV
            	data[0] = res[4];
                data[1] = res[5];
                data[2] = res[6];
                data[3] = res[7];
                data[4] = res[8];
                data[5] = res[9];
                data[6] = (byte) 0xF0;
                data[7] = (byte) i;
                data[8] = res[4];
                data[9] = res[5];
                data[10] = res[6];
                data[11] = res[7];
                data[12] = res[8];
                data[13] = res[9];
                data[14] = (byte) 0x0F;
                data[15] = (byte) i;
            }
        }

    }

    public class SecureChannelWrapper {

        private KeySet sessionKeys = null;

        private byte[] icv = null;

        private byte[] ricv = null;

        private int scp = 0;

        private ByteArrayOutputStream rMac;

        private boolean icvEnc;

        private boolean preAPDU, postAPDU;

        private boolean mac = false, enc = false, rmac = false;

        private SecureChannelWrapper(KeySet sessionKeys, int scp,
                int securityLevel, byte[] icv, byte[] ricv) {
            this.sessionKeys = sessionKeys;
            this.icv = icv;
            this.ricv = ricv;
            setSCPVersion(scp);
            setSecurityLevel(securityLevel);
        }

        public void setSecurityLevel(int securityLevel) {
            if ((securityLevel & APDU_MAC) != 0) {
                mac = true;
            } else {
                mac = false;
            }
            if ((securityLevel & APDU_ENC) != 0) {
                enc = true;
            } else {
                enc = false;
            }

            if ((securityLevel & APDU_RMAC) != 0) {
                rmac = true;
            } else {
                rmac = false;
            }

        }

        public void setSCPVersion(int scp) {
            this.scp = 2;
            if (scp < SCP_02_04) {
                this.scp = 1;
            }
            if (scp == SCP_01_15 || scp == SCP_02_14 || scp == SCP_02_15
                    || scp == SCP_02_1A || scp == SCP_02_1B) {
                icvEnc = true;
            } else {
                icvEnc = false;
            }

            if (scp == SCP_01_05 || scp == SCP_01_15 || scp == SCP_02_04
                    || scp == SCP_02_05 || scp == SCP_02_14 || scp == SCP_02_15) {
                preAPDU = true;
            } else {
                preAPDU = false;
            }
            if (scp == SCP_02_0A || scp == SCP_02_0B || scp == SCP_02_1A
                    || scp == SCP_02_1B) {
                postAPDU = true;
            } else {
                postAPDU = false;
            }
        }

        private byte clearBits(byte b, byte mask) {
            return (byte) ((b & ~mask) & 0xFF);
        }

        private byte setBits(byte b, byte mask) {
            return (byte) ((b | mask) & 0xFF);
        }

        private CommandAPDU wrap(CommandAPDU command) throws CardException {
            try {
                if (rmac) {
                	if(rMac==null)
                		rMac = new ByteArrayOutputStream();
                    rMac.reset();
                    rMac.write(clearBits((byte) command.getCLA(), (byte) 0x07));
                    rMac.write(command.getINS());
                    rMac.write(command.getP1());
                    rMac.write(command.getP2());
                    if (command.getNc() >= 0) {
                        rMac.write(command.getNc());
                        rMac.write(command.getData());
                    }
                }
                if (!mac && !enc) {
                    return command;
                }

                int origCLA = command.getCLA();
                int newCLA = origCLA;
                int origINS = command.getINS();
                int origP1 = command.getP1();
                int origP2 = command.getP2();
                byte[] origData = command.getData();
                int origLc = command.getNc();
                int newLc = origLc;
                byte[] newData = null;
                int le = command.getNe();
                ByteArrayOutputStream t = new ByteArrayOutputStream();

                int maxLen = 255;

                if (mac)
                    maxLen -= 8;
                if (enc)
                    maxLen -= 8;

                if (origLc > maxLen) {
                    throw new CardException("APDU too long for wrapping.");
                }

                if (mac) {

                    if (icv == null) {
                        icv = new byte[8];
                    } else if (icvEnc) {
                        ICipher c = null;
                        if (scp == 1) {
                            c = ICipher.Factory.getImplementation(
                                    ICipher.DESEDE_ECB_NOPADDING, GPUtil
                                            .getKey(sessionKeys.keys[1], 24));
                        } else {
                            c = ICipher.Factory.getImplementation(
                                    ICipher.DES_ECB_NOPADDING, GPUtil.getKey(
                                            sessionKeys.keys[1], 8));
                        }
                        icv = c.encrypt(icv);
                    }

                    if (preAPDU) {
                        newCLA = setBits((byte) newCLA, (byte) 0x04);
                        newLc = newLc + 8;
                    }
                    t.write(newCLA);
                    t.write(origINS);
                    t.write(origP1);
                    t.write(origP2);
                    t.write(newLc);
                    t.write(origData);

                    if (scp == 1) {
                        icv = GPUtil.mac_3des(sessionKeys.keys[1], GPUtil
                                .pad80(t.toByteArray()), icv);
                    } else {
                        icv = GPUtil.mac_des_3des(sessionKeys.keys[1], GPUtil
                                .pad80(t.toByteArray()), icv);
                    }

                    if (postAPDU) {
                        newCLA = setBits((byte) newCLA, (byte) 0x04);
                        newLc = newLc + 8;
                    }
                    t.reset();
                    newData = origData;
                }

                if (enc && origLc > 0) {
                    if (scp == 1) {
                        t.write(origLc);
                        t.write(origData);
                        if (t.size() % 8 != 0) {
                            byte[] x = GPUtil.pad80(t.toByteArray());
                            t.reset();
                            t.write(x);
                        }
                    } else {
                        t.write(GPUtil.pad80(origData));
                    }
                    newLc += t.size() - origData.length;

                    ICipher c = ICipher.Factory.getImplementation(
                            ICipher.DESEDE_CBC_NOPADDING, GPUtil.getKey(
                                    sessionKeys.keys[0], 24), new byte[8]);
                    newData = c.encrypt(t.toByteArray());
                    t.reset();
                }
                t.write(newCLA);
                t.write(origINS);
                t.write(origP1);
                t.write(origP2);
                if (newLc > 0) {
                    t.write(newLc);
                    t.write(newData);
                }
                if (mac) {
                    t.write(icv);
                }
                if (le > 0) {
                    t.write(le);
                }
                CommandAPDU wrapped = new CommandAPDU(t.toByteArray());
                return wrapped;
            } catch (CardException ce) {
                throw ce;
            } catch (Exception e) {
                throw new CardException("APDU wrapping failed.", e);
            }
        }

        private ResponseAPDU unwrap(ResponseAPDU response) throws CardException {
            if (rmac) {
                if (response.getData().length < 8) {
                    throw new CardException(
                            "Wrong response length (too short).");
                }
                int respLen = response.getData().length - 8;
                rMac.write(respLen);
                rMac.write(response.getData(), 0, respLen);
                rMac.write(response.getSW1());
                rMac.write(response.getSW2());

                ricv = GPUtil.mac_des_3des(sessionKeys.keys[3], GPUtil
                        .pad80(rMac.toByteArray()), ricv);

                byte[] actualMac = new byte[8];
                System.arraycopy(response.getData(), respLen, actualMac, 0, 8);
                if (!Arrays.equals(ricv, actualMac)) {
                    throw new CardException("RMAC invalid.");
                }
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                o.write(response.getBytes(), 0, respLen);
                o.write(response.getSW1());
                o.write(response.getSW2());
                response = new ResponseAPDU(o.toByteArray());
            }
            return response;

        }

    }

    private static final String jcopProviderName = "ds.javacard.emulator.jcop.DS_provider";

    public static void loadJCOPProvider() throws InstantiationException,
            ClassNotFoundException, IllegalAccessException,
            NoSuchAlgorithmException {
        Class<?> jcopProvider = Class.forName(jcopProviderName);
        Security.addProvider((Provider) (jcopProvider.newInstance()));
        // Peek that provider to provoke ClassNotFoundException
        // from a missing offcard.jar.
        TerminalFactory.getInstance("JcopEmulator", null);
        System.out.println("Provider for jcop emulator comptibility loaded.");
    }

    public String getLine() throws IOException
    {
		System.out.print(">");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		return in.readLine();
    }
    
    public boolean runScript(String script)
    {
    	BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(script));
		} catch (FileNotFoundException e) {
			return false;
		}
		String line = null;
		while(true)
		{
			line="";
			int ch = 0;
			try {
				while ((ch = br.read()) > -1 && ((char)ch)!='\n' && ((char)ch)!='\r') {
					if((char)ch==' ' && line.equals(""))
						continue;
					line+=(char)ch;
				}
			} catch (IOException e1) {
				try {
					br.close();
				} catch (IOException e) {
					return true;
				}
				return true;
			}
			if(((char)ch)=='\r')
				try {
					if(br.readLine()==null)
						break;
				} catch (IOException e) {
					try {
						br.close();
					} catch (IOException e1) {
						return true;
					}
					return true;
				}
			
			if (line.isEmpty() || line.trim().equals("") || line.startsWith("#"))
			{
				if(ch==-1)
					break;
				continue;
			}
			
			this.commandLine(line);
			if(ch==-1)
				break;
		}
		try {
			br.close();
		} catch (IOException e) {
			return true;
		}
    	return true;
    }
    
    @SuppressWarnings("unchecked")
	public void putHelpMap()
    {
    	helpMap = new HashMap<String,String>();
    	helpMap.put("help","this");
    	helpMap.put("/set-var","/set-var <path=\"script\">  set a shell variable");
    	helpMap.put("delete","delete [-r|--delete-related] <aid> delete an aid");
    	helpMap.put("install","install -i <instance aid> C9#([params]) <package aid> <applet aid>  install for install an applet");
    	helpMap.put("auth","auth [plain|enc|mac|rmac] [keyId]  open a secure channel");
    	helpMap.put("select","select <aid>  select an AID");
    	helpMap.put("/select","/select <aid>  select an AID, send raw (no wrapping)");
    	helpMap.put("send","send <apdu-c>  send an apdu");
    	helpMap.put("encrypt","encrypt <apdu-c>  send an apdu, but encrypt the payload with the KEK prior to wrapping");
    	helpMap.put("/send","/send <apdu-c>  select an apdu, send raw (no wrapping)");
    	helpMap.put("/card","/card    reset the card and select the GP card manager");
    	helpMap.put("/atr","/atr   reset the card");
    	helpMap.put("set-key","set-key key-set/key-id(1|2|3)/key-type(DES|DES-ECB|DES-CBC)/key-value(16 byte double length key) set a key in the shell ");
    	helpMap.put("put-key","set-key -m|--mode add|replace [-r|--replace-keySet key-set] key-set/key-id(1|2|3)/key-type(DES|DES-ECB|DES-CBC)/key-value(16 byte double length key) set a key in the shell ");
       }
    
    public void printAllHelp()
    {
    	@SuppressWarnings("unchecked")
		Set<String> keys = helpMap.keySet();
    	Iterator<String> i = keys.iterator();
    	while ( i.hasNext() ){
    		System.out.println(i.next());
    	}
    }
    
    void printHelp(String cmd)
    {
    	if(helpMap.get(cmd)!=null)
    		System.out.println(helpMap.get(cmd));
    }
    
    public void commandLine(String cmd)
    {
    	String cmdLine[] = cmd.split("\\s+");
    	AID instanceAid = null;
    	AID appletAid = null;
    	AID packageAid = null;
    	byte[] params = null;
    	if(cmdLine.length>0 && !cmdLine[0].equals(""))
    	{
    		switch(cmdLine[0])
    		{
    		case "put-key":
    			if(wrapper==null)
    			{
					System.out.println("command not supported");
					return;
    			}
    			if(cmdLine.length>3)
    			{
					if(!cmdLine[1].equals("-m") && !cmdLine[1].equals("--mode"))
					{
						System.out.println("invalid parameter");
						return;
					}
					if(!cmdLine[2].equals("add") && !cmdLine[2].equals("replace"))
					{
						System.out.println("invalid parameter");
						return;
					}
					short index = 3;
					if(cmdLine[2].equals("replace") && !cmdLine[3].equals("-r") && !cmdLine[3].equals("--replace-keySet"))
					{
						System.out.println("invalid parameter");
						return;
					}
					else if(cmdLine[2].equals("replace"))
					{
						if(cmdLine.length<6)
						{
							System.out.println("invalid parameter");
							return;
						}
						index+=2;
						try {
							if(Integer.parseInt(cmdLine[4])<1 || Integer.parseInt(cmdLine[4])>127)
							{
								System.out.println("invalid parameter");
								return;
							}
						} catch(NumberFormatException e) {
							System.out.println("invalid format");
							return;
						}
					}
					
    				
    				String[][] keyParts = new String[cmdLine.length-index][];
    				for(short i=index;i<cmdLine.length;i++)
    				{
    					keyParts[i-index] = cmdLine[i].split("[.,\\|;:/]");
    					if(keyParts[i-index]==null || keyParts[i-index].length!=4)
    					{
    						System.out.println("command not supported");
    						return;
    					}
    					try{
    						if(Integer.parseInt(keyParts[i-index][0])<=0 || Integer.parseInt(keyParts[i-index][0])>127)
    						{
        						System.out.println("invalid key set number");
        						return;
    						}
    						if(Integer.parseInt(keyParts[i-index][1])<1 || Integer.parseInt(keyParts[i-index][1])>3)
    						{
        						System.out.println("invalid key id");
        						return;
    						}
    					} catch(NumberFormatException e) {
    						System.out.println("invalid format");
    						return;
    					}
    					switch(keyParts[i-index][2])
    					{
    					case "DES-ECB":
    					case "DES":
        						break;
    					default:
    						System.out.println("invalid encryption alg");
    						return;
    					}
    					if(GPUtil.stringToByteArray(keyParts[i-index][3]).length!=16)
    					{
    						System.out.println("invalid key");
    						return;
    					}
    				}
    				
    				byte P1 = 0x00;
    				if(!cmdLine[2].equals("add"))
    					P1|=Integer.parseInt(cmdLine[4]);
    				byte keyId = (byte)Integer.parseInt(keyParts[0][1]);
    				byte P2 = keyId;
    				if(keyParts.length>1)
    					P2 = (byte)(P2|0x80);
    				byte[] data = new byte[0x16*keyParts.length+1];
    				if((byte)Integer.parseInt(keyParts[0][0])==0)
					{
						System.out.println("invalid key data");
						return;
					}
    				data[0] = (byte)Integer.parseInt(keyParts[0][0]);
    	    				
    				//k, i have at least one key now
    				for(short i=0;i<keyParts.length;i++)
    				{
    					if(keyId!= Integer.parseInt(keyParts[i][1]))
    					{
    						System.out.println("invalid key data");
    						return;
    					}
    					else if(data[0] != (byte)Integer.parseInt(keyParts[i][0]))
    					{
    						System.out.println("invalid key data");
    						return;
    					}
    					keyId++;
    					data[1 + i*0x16] = (byte)0x81;
    					data[1 + i*0x16+1] = (byte)0x10;
    					byte[] key = GPUtil.stringToByteArray(keyParts[i][3]);
    					byte[] keyCheck = new byte[3];
    					try {
							byte[] ch = GPUtil.ecb3des(key, new byte[]{0,0,0,0,0,0,0,0}, 0, 8);
							keyCheck[0] = ch[0];
							keyCheck[1] = ch[1];
							keyCheck[2] = ch[2];
						} catch (CardException e1) {
							System.out.println("invalid key data");
							return;
						}
    					try {
							key = GPUtil.ecb3des(wrapper.sessionKeys.keys[2], key, 0, 0x10);
						} catch (CardException e) {
    						System.out.println("invalid key data");
    						return;
						}
    					System.arraycopy(key, 0, data, 1 + i*0x16+2, 0x10);
    					data[i*0x17+19] = 0x03;
    					System.arraycopy(keyCheck, 0, data, 1 + i*0x16+19, 3);    					
    				}
    				try {
						this.transmit(new CommandAPDU(0x80,0xD8,P1,P2,data,0x00));
					} catch (IllegalStateException e) {
						System.out.println("command not supported");
		    		} catch (CardException e) {
		    			System.out.println("command not supported");
		        	}
    			}
    			else
    				System.out.println("command not supported");
    			break;
    		case "set-key":
    			if(cmdLine.length>1)
    			{
    				String[][] keyParts = new String[cmdLine.length-1][];
    				for(short i=1;i<cmdLine.length;i++)
    				{
    					keyParts[i-1] = cmdLine[i].split("[.,\\|;:/]");
    					if(keyParts[i-1]==null || keyParts[i-1].length!=4)
    					{
    						System.out.println("command not supported");
    						return;
    					}
    					try{
    						if(Integer.parseInt(keyParts[i-1][0])<=0 || Integer.parseInt(keyParts[i-1][0])>127)
    						{
        						System.out.println("invalid key set number");
        						return;
    						}
    						if(Integer.parseInt(keyParts[i-1][1])<1 || Integer.parseInt(keyParts[i-1][1])>3)
    						{
        						System.out.println("invalid key id");
        						return;
    						}
    					} catch(NumberFormatException e) {
    						System.out.println("invalid format");
    						return;
    					}
    					switch(keyParts[i-1][2])
    					{
    					case "DES":
    					case "DES-ECB":
    					case "DES-CBC":
        					break;
    					default:
    						System.out.println("invalid encryption alg");
    						return;
    					}
    					if(GPUtil.stringToByteArray(keyParts[i-1][3]).length!=16)
    					{
    						System.out.println("invalid key");
    						return;
    					}
    				}
    				//k, i have at least one key now
    				for(short i=0;i<keyParts.length;i++)
    				{
    					this.setKey(Integer.parseInt(keyParts[i][0]), Integer.parseInt(keyParts[i][1]), keyParts[i][2], GPUtil.stringToByteArray(keyParts[i][3]));
    				}
    			}
    			else
    				System.out.println("command not supported");
    			break;
    		case "help":
    			if(cmdLine.length>1)
    				printHelp(cmdLine[1]);
    			else
    				printAllHelp();
    			break;
        	case "/set-var":
    			if(cmdLine.length>1)
    			{
    				cmd = cmd.replace("/set-var","");
    				Pattern p = Pattern.compile("\\s*path\\s*=\\s*\"([^\"]+)\".*");
    				Matcher m = p.matcher(cmd);
    				if(m.matches())
    					path = m.group(1);
    				else
    				{
    					p = Pattern.compile("\\s*path\\s*=\\s*([^ ]+).*");
        				m = p.matcher(cmd);
        				if(m.matches())
        					path = m.group(1);
        				else
        					System.out.println("command not supported");
    				}
    			}
    			else
    				System.out.println("command not supported");
    			break;
    		case "delete":
    			if(cmdLine.length>1 && cmdLine.length<4)
    			{
    				short i=1;
    				boolean deps = false;
    				if(cmdLine.length>1 && !cmdLine[1].equals(""))
    				{
    					if(cmdLine[1].equals("-r") || cmdLine[1].equals("--delete-related"))
    					{
    						deps = true;
    						i++;
    					}
    				}
 
    				byte[] aid = null;
					if((cmdLine[i].startsWith("|")))
						aid = GPUtil.readableStringToByteArray(cmdLine[i]);
					else
						aid = GPUtil.stringToByteArray(cmdLine[i]);
		   			try {
						this.deleteAID(new AID(aid), deps);
					} catch (GPDeleteException e) {
						System.out.println("command not supported");
					} catch (CardException e) {
						System.out.println("command not supported");
					}
    			}
    			break;
    		case "install":
    			if(cmdLine.length>1 && cmdLine.length<8)
    			{
    				short i = 1;
    				if(cmdLine.length>1 && !cmdLine[1].equals(""))
    				{
    					if(!cmdLine[1].equals("-i") && !cmdLine[1].equals("--instance-aid"))
    					{
    						System.out.println("command not supported");
    						return;
    					}
    				}
    				i++;
					if(cmdLine.length>i)
					{ 
						byte[] aid = null;
						if((cmdLine[i].startsWith("|")))
							aid = GPUtil.readableStringToByteArray(cmdLine[i]);
						else
							aid = GPUtil.stringToByteArray(cmdLine[i]);
						if(instanceAid==null)
							instanceAid = new AID(aid);
						i++;
					}
					else
						System.out.println("command not supported");
    				while(i<cmdLine.length)
    				{
	    				switch(cmdLine[i])
	    				{
	    				case "-q":
	    					i++;
	    					if(cmdLine.length>i)
	    					{
	    						//C9#(XX)
	    						if(cmdLine[i].startsWith("C9#(") && cmdLine[i].endsWith(")"))
	    						{
	    							if((cmdLine[i].length()-2)>4 && (cmdLine[i].substring(4,cmdLine[i].length()-1).startsWith("|")))
		        					{
		        						byte[] b = GPUtil.readableStringToByteArray(cmdLine[i].substring(4,cmdLine[i].length()-1));
			        					params = new byte[b.length+2];
		        						System.arraycopy(b, 0, params, 2, b.length);
		        						params[0] = (byte)0xC9;
			        					params[1] = (byte)b.length;
		        					}
		        					else if((cmdLine[i].length()-2)>4)
		        					{
		        						byte[] b = GPUtil.stringToByteArray(cmdLine[i].substring(4,cmdLine[i].length()-1));
			        					params = new byte[b.length+2];
		        						System.arraycopy(b, 0, params, 2, b.length);
		        						params[0] = (byte)0xC9;
			        					params[1] = (byte)b.length;
		        					}
	    						}
	    						i++;
	    					}
	    					break;
	    				default:
	    					if(!cmdLine[i].equals(""))
	    					{
	    						byte[] aid = null;
	        					if((cmdLine[i].startsWith("|")))
	        						aid = GPUtil.readableStringToByteArray(cmdLine[i]);
	        					else
	        						aid = GPUtil.stringToByteArray(cmdLine[i]);
	    						if(packageAid==null)
	    							packageAid = new AID(aid);
	    						else if(appletAid == null)
	    							appletAid = new AID(aid);
	    						else
	    						{
	    							System.out.println("command not supported");
	    							return;
	    						}
	    					}
	    					i++;
	    					break;
	    				}
    				}
    			}
    			else
    			{
    				System.out.println("command not supported");
    				return;
    			}
    			if(packageAid==null || appletAid==null)
    			{
    				System.out.println("command not supported");
    				return;
    			}
    			
    			try {
					this.installAndMakeSelecatable(
							packageAid, appletAid,
							instanceAid, (byte) 0,
							params, null);
				} catch (GPMakeSelectableException e3) {
    				System.out.println("command not supported");
				} catch (CardException e3) {
    				System.out.println("command not supported");
				}

    			break;
    		case "auth":
    			if(cmdLine.length<4)
    			{
    				int mode = APDU_CLR;
    				if(cmdLine.length>1 && !cmdLine[1].equals(""))
    				{
    					String[] apduMode = cmdLine[1].split("\\|");
    					for(short i=0;i<apduMode.length;i++)
    					{
	    					switch(apduMode[i])
	    					{
	    					case "plain":
	    						mode = mode|APDU_CLR;
	    						break;
	    					case "enc":
	    						mode = mode|APDU_ENC|APDU_MAC;
	    						break;
	    					case "mac":
	    						mode = mode|APDU_MAC;
	    						break;
	    					case "rmac":
	    						mode = mode|APDU_RMAC;
	    						break;
	    					default:
	    	    				System.out.println("command not supported");
	    	    				return;
	    					}
    					}
    				}
    				int keySet = 0;
    				if(cmdLine.length>2 && !cmdLine[2].equals(""))
    				{
    					try {
    						keySet = Integer.parseInt(cmdLine[2]);
    					} catch (NumberFormatException e) {
    	    				System.out.println("command not supported");
    	    				return;
    					}
    				}
		    		try {
						this.openSecureChannel(keySet, 0,
								GlobalPlatformService.SCP_ANY,
								mode, false);
					} catch (IllegalArgumentException e) {
	    				System.out.println("invalid arguments");
					} catch (CardException e) {
	    				System.out.println("command not supported");
					}
    			}
    			else
    				System.out.println("command not supported");
    			break;
    		case "/select":
    			if(cmdLine.length==2 && !cmdLine[1].equals(""))
    			{
	    			try {
	    				CommandAPDU c;
    					ResponseAPDU r;
    					if((cmdLine[1].startsWith("|")))
    						c = new CommandAPDU(0x00,0xA4,0x04,0x00,GPUtil.readableStringToByteArray(cmdLine[1]));
    					else
    						c = new CommandAPDU(0x00,0xA4,0x04,0x00,GPUtil.stringToByteArray(cmdLine[1]));
    					long t = System.currentTimeMillis();
    					r = channel.transmit(c);
    					notifyExchangedAPDU(c, r, (int)(System.currentTimeMillis()-t));
    			    } catch (IllegalStateException e) {
						System.out.println("data format not supported");
					} catch (CardException e) {
						System.out.println("data format not supported");
					}
	    			wrapper = null;
        		}
    			else
    				System.out.println("command not supported");
    			break;
    		case "select":
    			if(cmdLine.length==2 && !cmdLine[1].equals(""))
    			{
    				try {
    					if((cmdLine[1].startsWith("|")))
    	    				this.transmit(new CommandAPDU(0x00,0xA4,0x04,0x00,GPUtil.readableStringToByteArray(cmdLine[1])));
    					else
    	    				this.transmit(new CommandAPDU(0x00,0xA4,0x04,0x00,GPUtil.stringToByteArray(cmdLine[1])));
					} catch (IllegalStateException e) {
						System.out.println("data format not supported");
					} catch (CardException e) {
						System.out.println("data format not supported");
					}
    				wrapper = null;
        		}
    			else
    				System.out.println("command not supported");
    			break;
    		case "/send":
    			if(cmdLine.length==2 && !cmdLine[1].equals(""))
    			{
	    			CommandAPDU c = new CommandAPDU(GPUtil.stringToByteArray(cmdLine[1]));
	    			try {
	    				long t = System.currentTimeMillis();
	    				ResponseAPDU r = channel.transmit(c);
						notifyExchangedAPDU(c, r, (int)(System.currentTimeMillis()-t));
			    	} catch (CardException e2) {
			    		System.out.println("data format not supported");
					}
    			}
    			else
    				System.out.println("command not supported");
    			break;
    		case "send":
    			if(cmdLine.length==2 && !cmdLine[1].equals(""))
    			{
    				//decrypt blocks if any
    				if(wrapper!=null)
    				{
    					String data = cmdLine[1];
    					String newData = "";
    					String enc = "";
    					boolean encOn = false;    					
    					for(short i=0;i<data.length();i++)
    					{
    						if(data.subSequence(i, i+1).equals("+"))
    						{
    							if(!encOn)
    								encOn = true;
    							else
    							{
    								encOn = false;
    								byte[] d = GPUtil.stringToByteArray(enc);
    								enc="";
    								if(d==null || (d.length%8)!=0 || (!wrapper.sessionKeys.type.equals("DES_ECB") && !wrapper.sessionKeys.type.equals("DES")))
    		    					{
    		    						System.out.println("data format not supported");
    		    						return;
    		    					}
    								//encrypt the data now
    								try {
										d = GPUtil.ecb3des(wrapper.sessionKeys.keys[2], d, 0, d.length);
										newData+=GPUtil.byteArrayToString(d);
		    		    			} catch (CardException e) {
		    		    				System.out.println("data format not supported");
		        						return;
		        					}
    							}
    							continue;
    						}
    						if(!encOn)
    							newData+=data.subSequence(i, i+1);
    						else
    							enc+=data.subSequence(i, i+1);
    							
    					}
    					if(encOn==true)
    					{
    						System.out.println("data format not supported");
    						return;
    					}
						cmdLine[1] = newData;
    				}
    				
    				CommandAPDU c = new CommandAPDU(GPUtil.stringToByteArray(cmdLine[1]));
    				try {
    					this.transmit(c);
					} catch (IllegalStateException e) {
						System.out.println("data format not supported");
					} catch (CardException e) {
						System.out.println("data format not supported");
					}
        		}
    			else
    				System.out.println("command not supported");
    			break;
    		case "put":
    			break;
    		case "/card":
    			try {
    				this.channel.getCard().transmitControlCommand(0, null);
    				System.out.println("ATR: "
    						+ GPUtil.byteArrayToString(this.channel.getCard().getATR().getBytes()));
    				wrapper = null;
    				this.open();
    			} catch (GPSecurityDomainSelectionException e1) {
    	   			System.out.println("command not supported");
    			} catch (CardException e1) {
    	   			System.out.println("command not supported");
    			}
    			break;
    		case "/atr":
    			try {
    				this.channel.getCard().transmitControlCommand(0, null);
    				System.out.println("ATR: "
    						+ GPUtil.byteArrayToString(this.channel.getCard().getATR().getBytes()));
    				wrapper = null;
    			} catch (CardException e) {
    	   			System.out.println("command not supported");
    			}
    			break;
    		default:
    			if(path!=null && !path.equals(""))
    			{
    				if(cmdLine[0]!=null && cmdLine[0].length()>0)
    				{
    					if(!runScript(path+cmdLine[0]+".jcsh"))
    						System.out.println("command not supported");
    				}
    				else
        				System.out.println("command not supported");
    			}
    			else
    				System.out.println("command not supported");
    			break;
    		}
    	}
    }
    
    public static void usage() {
        System.out.println("Usage:");
        System.out
                .println("  java cardservices.GlobalPlatformService <options>");
        System.out.println("");
        System.out.println("Options:\n");
        System.out
                .println(" -sdaid <aid>      Security Domain AID, default a000000003000000");
        System.out.println(" -keyset <num>     use key set <num>, default 0");
        System.out.println(" -mode <apduMode>  use APDU mode, CLR, MAC, or ENC, default CLR");
        System.out
                .println(" -enc <key>        define ENC key, default: 40..4F");
        System.out
                .println(" -mac <key>        define MAC key, default: 40..4F");
        System.out
                .println(" -kek <key>        define KEK key, default: 40..4F");
        System.out
                .println(" -"+AID.GEMALTO+" use special VISA2 key diversification for GemaltoXpressPro cards");
        System.out
                .println("                   uses predifined Gemalto mother key if not stated otherwise");
        System.out
                .println("                   with -enc/-mac/-kek AFTER this option");
        System.out
                .println(" -visa2            use VISA2 key diversification (only key set 0), default off");
        System.out
                .println(" -emv              use EMV key diversification (only key set 0), default off");
        System.out
                .println(" -deletedeps       also delete depending packages/applets, default off");
        System.out.println(" -delete <aid>     delete package/applet");
        System.out.println(" -load <cap>       load <cap> file to the card, <cap> can be file name or URL");
        System.out.println(" -loadsize <num>   load block size, default "
                + defaultLoadSize);
        System.out
                .println(" -loadsep          load CAP components separately, default off");
        System.out
                .println(" -loaddebug        load the Debug & Descriptor component, default off");
        System.out
                .println(" -loadparam        set install for load code size parameter");
        System.out
                .println("                      (e.g. for CyberFlex cards), default off");
        System.out.println(" -loadhash         check code hash during loading");
        System.out.println(" -install          install applet:");
        System.out
                .println("   -applet <aid>   applet AID, default: take all AIDs from the CAP file");
        System.out
                .println("   -package <aid>  package AID, default: take from the CAP file");
        System.out.println("   -priv <num>     privileges, default 0");
        System.out
                .println("   -param <bytes>  install parameters, default: C900");
        System.out.println(" -list             list card registry");
        System.out
                .println(" -jcop             connect to the jcop emulator on port 8015");
        System.out.println(" -h|-help|--help   print this usage info");
        System.out.println(" -t Remote|localhost:3000   connect to a socket terminal");
        System.out.println(" -cmd   block on command line");
        System.out.println(" -s   <script/to/run.jcsh>  run a script");
        System.out.println("");
        System.out
                .println("Multiple -load/-install/-delete and -list take the following precedence:");
        System.out.println("  delete(s), load, install(s), list\n");
        System.out
                .println("All -load/-install/-delete/-list actions will be performed on\n"
                        + "the basic logical channel of all cards currently connected.\n"
                        + "By default all connected PC/SC terminals are searched.\n\n"
                        + "Option -jcop requires jcopio.jar and offcard.jar on the class path.\n");
        System.out
                .println("<aid> can be of the byte form 0A00000003... or the string form \"|applet.app|\"\n");
        System.out.println("Examples:\n");
        System.out.println(" [prog] -list");
        System.out.println(" [prog] -load applet.cap -install -list ");
        System.out
                .println(" [prog] -deletedeps -delete 360000000001 -load applet.cap -install -list");
        System.out
                .println(" [prog] -emv -keyset 0 -enc 404142434445464748494A4B4C4D4E4F -list");
        System.out.println("");
    }

    public static void main(String[] args) throws IOException {

    	final class InstallEntry {
    		AID appletAID;

    		AID packageAID;

    		int priv;

    		byte[] params;
    	}

    	boolean listApplets = false;

    	boolean use_jcop_emulator = false;
    	
    	boolean commandLine = false;
    	
    	String script = null;

    	String use_jc_remote_terminal = null;

    	int keySet = 0;
    	byte[][] keys = { defaultEncKey, defaultMacKey, defaultKekKey };
    	AID sdAID = null;
    	int diver = DIVER_NONE;
    	boolean gemalto = false;

    	Vector<AID> deleteAID = new Vector<AID>();
    	boolean deleteDeps = false;

    	URL capFileUrl = null;
    	int loadSize = defaultLoadSize;
    	boolean loadCompSep = false;
    	boolean loadDebug = false;
    	boolean loadParam = false;
    	boolean useHash = false;
    	int apduMode = APDU_CLR;

    	Vector<InstallEntry> installs = new Vector<InstallEntry>();

    	try {
    		for (int i = 0; i < args.length; i++) {

    			if (args[i].equals("-h") || args[i].equals("-help")
    					|| args[i].equals("--help")) {
    				usage();
    				System.exit(0);
    			}
    			if (args[i].equals("-list")) {
    				listApplets = true;
    			} else if (args[i].equals("-keyset")) {
    				i++;
    				keySet = Integer.parseInt(args[i]);
    				if (keySet <= 0 || keySet > 127) {
    					throw new IllegalArgumentException("Key set number "
    							+ keySet + " out of range.");
    				}
    			} else if (args[i].equals("-sdaid")) {
    				i++;
    				byte[] aid = GPUtil.stringToByteArray(args[i]);
    				if (aid == null) {
    					aid = GPUtil.readableStringToByteArray(args[i]);
    				}
    				if (aid == null) {
    					throw new IllegalArgumentException("Malformed SD AID: "
    							+ args[i]);
    				}
    				sdAID = new AID(aid);
    			} else if (args[i].equals("-"+AID.GEMALTO)) {
    				byte[] gemMotherKey = SPECIAL_MOTHER_KEYS.get(AID.GEMALTO);
    				keys = new byte[][] {gemMotherKey, gemMotherKey, gemMotherKey};
    				gemalto = true;
    				diver = DIVER_VISA2;
    			} else if (args[i].equals("-visa2")) {
    				diver = DIVER_VISA2;
    			} else if (args[i].equals("-emv")) {
    				diver = DIVER_EMV;
    			} else if (args[i].equals("-mode")) {
    				i++;
    				// TODO: RMAC modes
    				if("CLR".equals(args[i])) {
    					apduMode = APDU_CLR;
    				} else if("MAC".equals(args[i])) {
    					apduMode = APDU_MAC;
    				}else if ("ENC".equals(args[i])) {
    					apduMode = APDU_ENC;
    				} else {
    					throw new IllegalArgumentException("Invalid APDU mode: "+args[i]);                        
    				}
    			} else if (args[i].equals("-delete")) {
    				i++;
    				byte[] aid = GPUtil.stringToByteArray(args[i]);
    				if (aid == null) {
    					aid = GPUtil.readableStringToByteArray(args[i]);
    				}
    				if (aid == null) {
    					throw new IllegalArgumentException("Malformed AID: "
    							+ args[i]);
    				}
    				deleteAID.add(new AID(aid));
    			} else if (args[i].equals("-deletedeps")) {
    				deleteDeps = true;
    			} else if (args[i].equals("-loadsize")) {
    				i++;
    				loadSize = Integer.parseInt(args[i]);
    				if (loadSize <= 16 || loadSize > 255) {
    					throw new IllegalArgumentException("Load size "
    							+ loadSize + " out of range.");
    				}
    			} else if (args[i].equals("-loadsep")) {
    				loadCompSep = true;
    			} else if (args[i].equals("-loaddebug")) {
    				loadDebug = true;
    			} else if (args[i].equals("-loadparam")) {
    				loadParam = true;
    			} else if (args[i].equals("-loadhash")) {
    				useHash = true;
    			} else if (args[i].equals("-load")) {
    				i++;
    				try {
    					capFileUrl = new URL(args[i]);
    				}catch(MalformedURLException e) {
    					// Try with "file:" prepended
    					capFileUrl = new URL("file:"+args[i]);                        
    				}
    				try {
    					InputStream in = capFileUrl.openStream();
    					in.close();
    				}catch(IOException ioe) {
    					throw new IllegalArgumentException("CAP file "
    							+ capFileUrl + " does not seem to exist.", ioe);
    				}
    			} else if (args[i].equals("-install")) {
    				i++;
    				int totalOpts = 4;
    				int current = 0;
    				AID appletAID = null;
    				AID packageAID = null;
    				int priv = 0;
    				byte[] param = null;
    				while (i < args.length && current < totalOpts) {
    					if (args[i].equals("-applet")) {
    						i++;
    						byte[] aid = GPUtil.stringToByteArray(args[i]);
    						if (aid == null) {
    							aid = GPUtil.readableStringToByteArray(args[i]);
    						}
    						i++;
    						if (aid == null) {
    							throw new IllegalArgumentException(
    									"Malformed AID: " + args[i]);
    						}
    						appletAID = new AID(aid);
    						current = 1;
    					} else if (args[i].equals("-package")) {
    						i++;
    						byte[] aid = GPUtil.stringToByteArray(args[i]);
    						if (aid == null) {
    							aid = GPUtil.readableStringToByteArray(args[i]);
    						}
    						i++;
    						if (aid == null) {
    							throw new IllegalArgumentException(
    									"Malformed AID: " + args[i]);
    						}
    						packageAID = new AID(aid);
    						current = 2;
    					} else if (args[i].equals("-priv")) {
    						i++;
    						priv = Integer.parseInt(args[i]);
    						i++;
    						current = 3;
    					} else if (args[i].equals("-param")) {
    						i++;
    						param = GPUtil.stringToByteArray(args[i]);
    						i++;
    						if (param == null) {
    							throw new IllegalArgumentException(
    									"Malformed params: " + args[i]);
    						}
    						current = 4;
    					} else {
    						current = 4;
    						i--;
    					}
    				}
    				InstallEntry inst = new InstallEntry();
    				inst.appletAID = appletAID;
    				inst.packageAID = packageAID;
    				inst.priv = priv;
    				inst.params = param;
    				installs.add(inst);
    			} else if (args[i].equals("-jcop")) {
    				try {
    					loadJCOPProvider();
    					use_jcop_emulator = true;
    				} catch (Exception e) {
    					System.out
    					.println("Unable to load jcop compatibility provider.\n"
    							+ "Please put offcard.jar and jcopio.jar "
    							+ "on the class path.\n");
    					e.printStackTrace();
    					System.exit(1);
    				}
    			} else if (args[i].equals("-t")) {
    				i++;
    				use_jc_remote_terminal = args[i];
    			} else if (args[i].equals("-s")) {
    				i++;
    				script = args[i];
    			} else if (args[i].equals("-cmd")) {
    				commandLine = true;
    			} else {
    				String[] keysOpt = { "-enc", "-mac", "-kek" };
    				int index = -1;
    				for (int k = 0; k < keysOpt.length; k++) {
    					if (args[i].equals(keysOpt[k]))
    						index = k;
    				}
    				if (index >= 0) {
    					i++;
    					keys[index] = GPUtil.stringToByteArray(args[i]);
    					if (keys[index] == null || keys[index].length != 16) {
    						throw new IllegalArgumentException("Wrong "
    								+ keysOpt[index].substring(1).toUpperCase()
    								+ " key: " + args[i]);
    					}
    				} else {
    					throw new IllegalArgumentException("Unknown option: "
    							+ args[i]);
    				}
    			}
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
    		usage();
    		System.exit(1);
    	}

    	try {

    		/*
    		 * Provider acrProv = null; try { Class<?> acrProvClass =
    		 * Class.forName("ds.smartcards.acr122.ACR122Provider"); acrProv =
    		 * (Provider)acrProvClass.newInstance(); } catch (Exception e) { }
    		 * TerminalFactory tf = TerminalFactory.getInstance("ACR", null,
    		 * acrProv);
    		 */

    		TerminalFactory tf;
    		if(use_jc_remote_terminal!=null)
    			tf = null;
    		else if (use_jcop_emulator == false)
    			tf = TerminalFactory.getInstance("PC/SC", null);
    		else
    			tf = TerminalFactory.getInstance("JcopEmulator", null);

    		// System.out.println(tf.getProvider());
    		Card c = null;
    		CardTerminal terminal = null;
    		if(tf!=null)
    		{
    			CardTerminals terminals = tf.terminals();

    			System.out.println("Found terminals: " + terminals.list());
    			for (CardTerminal t : terminals
    					.list(CardTerminals.State.ALL)) {
    				terminal = t;
    				c = null;
    				try {
    					c = terminal.connect("*");
    				} catch (CardException e) {
    					if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_SMARTCARD")) {
    						System.err.println("No card in reader " + terminal.getName());
    						continue;
    					} else
    						e.printStackTrace();
    				}
    			}
    		}
    		else
    		{
    			String token = null;
    			short i;
    			for(i=0;i<use_jc_remote_terminal.length();i++)
    			{
    				if(use_jc_remote_terminal.substring(i,i+1).equals("|"))
    				{
    					token = use_jc_remote_terminal.substring((i+1),(use_jc_remote_terminal.length()));
    					break;
    				}
    			}
    			if(token!=null)
    			{
    				String[] tokens = token.split(":");
    				if(tokens.length==2)
    				{
    					int port = Integer.parseInt(tokens[1]);
		    			terminal = new CloudTerminal(tokens[0],port);
		    			c = null;
		    			try {
		    				c = terminal.connect("*");
		    			} catch (CardException e) {
		    				if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_SMARTCARD")) {
		    					System.err.println("No card in reader " + terminal.getName());
		    				} else
		    					e.printStackTrace();
		    			}
    				}
    			}
    		}
    		if(c==null)
    		{
    			return;
    		}

    		try {
    			System.out.println("Found card in terminal: "
    					+ terminal.getName());
    			System.out.println("ATR: "
    					+ GPUtil.byteArrayToString(c.getATR().getBytes()));
    			CardChannel channel = c.getBasicChannel();
    			GlobalPlatformService service = (sdAID == null) ? new GlobalPlatformService(
    					channel)
    			: new GlobalPlatformService(sdAID, channel);
    					service.addAPDUListener(service);
    					service.setKeys(keySet, keys[0], keys[1], keys[2], diver);
    					if(deleteAID.size()>0 ||
    							installs.size()>0 ||
    							capFileUrl != null || 
    							listApplets    							
    							)
    					{
    						service.open();        					
        					// TODO: make the APDU mode a parameter, properly adjust
        					// loadSize accordingly
        					int neededExtraSize = apduMode == APDU_CLR ? 0 :
        						(apduMode == APDU_MAC ? 8 : 16);
        					if (loadSize + neededExtraSize > defaultLoadSize) {
        						loadSize -= neededExtraSize;
        					}
    						service.openSecureChannel(keySet, 0,
    							GlobalPlatformService.SCP_ANY,
    							apduMode, gemalto);
    					}

    					if (deleteAID.size() > 0) {
    						for (AID aid : deleteAID) {
    							try {
    								service.deleteAID(aid, deleteDeps);
    							} catch (CardException ce) {
    								System.out.println("Could not delete AID: "
    										+ aid);
    								// This is when the applet is not there, ignore
    							}
    						}
    					}
    					CapFile cap = null;

    					if (capFileUrl != null) {
    						cap = new CapFile(capFileUrl.openStream());
    						service.loadCapFile(cap, loadDebug, loadCompSep,
    								loadSize, loadParam, useHash);
    					}

    					if (installs.size() > 0) {
    						for (InstallEntry install : installs) {
    							if (install.appletAID == null) {
    								AID p = cap.getPackageAID();
    								for (AID a : cap.getAppletAIDs()) {
    									service.installAndMakeSelecatable(p, a,
    											null, (byte) install.priv,
    											install.params, null);
    								}
    							} else {
    								service.installAndMakeSelecatable(
    										install.packageAID, install.appletAID,
    										null, (byte) install.priv,
    										install.params, null);

    							}
    						}

    					}
    					if (listApplets) {
    						AIDRegistry registry = service.getStatus();
    						for (AIDRegistryEntry e : registry) {
    							AID aid = e.getAID();
    							int numSpaces = (15 - aid.getLength());
    							String spaces = "";
    							String spaces2 = "";
    							for (int i = 0; i < numSpaces; i++) {
    								spaces = spaces + "   ";
    								spaces2 = spaces2 + " ";
    							}
    							System.out.print("AID: "
    									+ GPUtil.byteArrayToString(aid.getBytes())
    									+ spaces
    									+ " "
    									+ GPUtil.byteArrayToReadableString(aid
    											.getBytes()) + spaces2);
    							System.out.format(" %s LC: %d PR: 0x%02X\n", e
    									.getKind().toShortString(), e
    									.getLifeCycleState(), e.getPrivileges());
    							for (AID a : e.getExecutableAIDs()) {
    								numSpaces = (15 - a.getLength()) * 3;
    								spaces = "";
    								for (int i = 0; i < numSpaces; i++)
    									spaces = spaces + " ";
    								System.out
    								.println("     "
    										+ GPUtil.byteArrayToString(a
    												.getBytes())
    												+ spaces
    												+ " "
    												+ GPUtil
    												.byteArrayToReadableString(a
    														.getBytes()));
    							}
    							System.out.println();
    						}

    					}
    					
    					if(script!=null)
    					{
    						service.runScript(script);
    					}
    					else
    					{
	    					/*
	    					 * shell
	    					 */
	    					while(commandLine)
	    					{
	    						service.commandLine(service.getLine());
	    					}
    					}
    					
    		} catch (Exception ce) {
    			ce.printStackTrace();
    		}
    	} catch (CardException e) {
    		if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_READERS_AVAILABLE"))
    			System.out.println("No smart card readers found");
    		else
    			e.printStackTrace();
    	} catch (NoSuchAlgorithmException e) {
    		if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_SERVICE"))
    			System.out.println("No smart card readers found (PC/SC service not running)");
    		else
    			e.printStackTrace();
    	} catch (Exception e) {
    		System.out.format("Terminated by escaping exception %s\n", e
    				.getClass().getName());
    		e.printStackTrace();
    	}
    }
}