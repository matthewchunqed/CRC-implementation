import java.util.Iterator;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Stack;

public class CRCDataLinkLayer extends DataLinkLayer {

    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */


    protected byte[] createFrame (byte[] data) {
    
	Queue<Byte> framingData = new LinkedList<Byte>();


	byte remainder = (byte)((0xff & data[0]));
	//System.out.println("remainder = " + Integer.toBinaryString(remainder));

	Queue<Integer> frameData = new LinkedList<Integer>();
	//frameData Holds the dividend for CRC calculation
	

	// Add each byte of original data.
	for (int i = 0; i < data.length; i += 1) {

		for(int j=7; j>=0; j--){
			//Add data to the dividend data structure, one bit at a time.
			frameData.add((((int)(0xff & data[i])) >> j) & 0b1);
		}

		// Begin every 8 bytes with the start tag.
		if(i % 8 == 0){
			framingData.add(startTag);
		
			//Reset the remainder counter for this frame
			remainder = (byte)((0xff & data[i]));
		}

	    // If the current data byte is itself a metadata tag, then precede
	    // it with an escape tag.
	    byte currentByte = data[i];

	    if ((currentByte == startTag) ||
		(currentByte == stopTag) ||
		(currentByte == escapeTag)) {

		framingData.add(escapeTag);

	    }

	    // Add the data byte itself.
	    framingData.add(currentByte);

		// End every 8 bits and the whole frameData with a stop tag.
		if(i % 8 == 7 || i == data.length-1){

			//Append zeroes to CRC frame data here.
			for(int j=0; j<8; j++){
				//Append zeroes to the dividend
				frameData.add(0);
			}
			for(int j=0; j<8; j++){
				//remove the first byte of the dividend data, since we just need to store the numbers we "pull down"
				frameData.remove();
			}

			//Calculate this frame's CRC remainder here.
			while(!frameData.isEmpty()){
				
				if((remainder & 0xff) >= 0b10000000){
					//if the remainder start with 0, XOR it, otherwise move on.
					remainder = (byte)((remainder & 0xff) ^ polynomial);
				}
				//move right down the dividend.
				remainder = (byte)(((remainder & 0xff) << 1) + frameData.remove());
			}
			System.out.println("adding remainder " + remainder);
			framingData.add(remainder);
			//Now, we have 8 bytes of data, followed by 1 byte of remainder metadata, surrounded by start & stop tags.
			framingData.add(stopTag);
		}

	}

	// Convert to the desired byte array.
	byte[] framedData = new byte[framingData.size()];
	Iterator<Byte>  i = framingData.iterator();
	int             j = 0;
	while (i.hasNext()) {
	    framedData[j++] = i.next();
	}

	return framedData;
	
    } // createFrame ()
    // =========================================================================


    
    // =========================================================================
    /**
     * Determine whether the received, buffered data constitutes a complete
     * frame.  If so, then remove the framing metadata and return the original
     * data.  Note that any data preceding an escaped start tag is assumed to be
     * part of a damaged frame, and is thus discarded.
     *
     * @return If the buffer contains a complete frame, the extracted, original
     * data; <code>null</code> otherwise.
     */
    protected byte[] processFrame () {
	
	// Search for a start tag.  Discard anything prior to it.
	boolean        startTagFound = false;
	Iterator<Byte>             i = byteBuffer.iterator();
	
	while (!startTagFound && i.hasNext()) {
	    byte current = i.next();
	    if (current != startTag) {
		i.remove();
	    } else {
		startTagFound = true;
	    }
	}
	
	// If there is no start tag, then there is no frame.
	if (!startTagFound) {
	    return null;
	}
	
	// Try to extract data while waiting for an unescaped stop tag.
	Queue<Byte> extractedBytes = new LinkedList<Byte>();

	Queue<Integer> frameData = new LinkedList<Integer>();
	//frameData stores the bits that is used for CRC calculation from the dividend.

	byte remainder = 0;
	//remainder will hold the calculated remainder from the data

	byte receivedRemainder = 0;
	//receivedRemainder will hold the remainder byte tag received from the data, and it will be compared to remainder.

	boolean       stopTagFound = false;

	//Mod 8 Counter to find byte preceding stop tag.
	byte counter = -1;
	boolean errorDetected = false;
	Stack<Byte> bytes = new Stack<Byte>();
	//This bytes stack is a helper to retrieve the last byte (the remainder byte)
	bytes.push((byte)0);
	bytes.push((byte)0);

	boolean foundFirstDataByte = false;

	while (!stopTagFound && i.hasNext()) {
		counter += 1;
	    // Grab the next byte.  If it is...
	    //   (a) An escape tag: Skip over it and grab what follows as
	    //                      literal data.
	    //   (b) A stop tag:    Remove all processed bytes from the buffer and
	    //                      end extraction.
	    //   (c) A start tag:   All that precedes is damaged, so remove it
	    //                      from the buffer and restart extraction.
	    //   (d) Otherwise:     Take it as literal data.
	    byte current = i.next();
		//Counter to count 8 bits of data.

			bytes.push(current);

	    if (current == escapeTag) {

		if (i.hasNext()) {
		    current = i.next();
		    extractedBytes.add(current);
			//Intake CRC Frame Data Here
			if(!foundFirstDataByte){
				//set the remainder to the first byte's worth of frameData. the first 8 digits of the dividend.
				foundFirstDataByte = true;
				remainder = current;
			}
			for(int j=7; j>=0; j--){
				//Add data to dividend, one bit at a time.
				frameData.add((((int)(0xff & current)) >> j) & 0b1);
			}

		} else {
		    // An escape was the last byte available, so this is not a
		    // complete frame.
		    return null;
		}
	    } else if (current == stopTag) {
		cleanBufferUpTo(i);
		stopTagFound = true;
	    } else if (current == startTag) {
		cleanBufferUpTo(i);
		extractedBytes = new LinkedList<Byte>();
	    } else {
		
		//Case 1: it's a remainder byte
		//Check the remainder byte, report error, then skip to the stop tag.
		if((counter > 0 && counter % 8 == 0)){

		}else{
		//Case 2: it's real data	
		extractedBytes.add(current);
		if(!foundFirstDataByte){
			//set the remainder to the first byte's worth of frameData. the first 8 digits of the dividend.
			foundFirstDataByte = true;
			remainder = current;
		}
		
		//Intake CRC Frame Data Here
		for(int j=7; j>=0; j--){
			//Add data to dividend, one bit at a time.
			frameData.add((((int)(0xff & current)) >> j) & 0b1);
		}

		}

	    }
	}
	bytes.pop();
	receivedRemainder = bytes.pop();
	//this second to last bit is the remainder byte that we'll compare to. 

	// If there is no stop tag, then the frame is incomplete.
	if (!stopTagFound) {
	    return null;
	}

	// Convert to the desired byte array.
	if (debug) {
	    System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
	}
	byte[] extractedData = new byte[extractedBytes.size()];
	if(extractedBytes.size() < 8){
		//remove the remainder byte from the received data
		extractedData = new byte[extractedBytes.size()-1];

		//this code all below removes the remainder byte from the dividend queue.
		Stack<Integer> frameDataCopy = new Stack<Integer>();
		while(!frameData.isEmpty()){
			frameDataCopy.push(frameData.remove());
		}
		for(int k=0; k<8; k++){
			frameDataCopy.pop();
		}
		Stack<Integer> frameData2 = new Stack<Integer>();
		while(!frameDataCopy.isEmpty()){
			frameData2.push(frameDataCopy.pop());
		}
		while(!frameData2.isEmpty()){
			frameData.add(frameData2.pop());
		}
		//ending here.

	}
	String contents = "";
	int j = 0;
	i = extractedBytes.iterator();
	while (j < extractedData.length) {
	    extractedData[j] = i.next();
		contents += (char)(extractedData[j]);
	    if (debug) {
		System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
				  j,
				  extractedData[j]);
	    }
	    j += 1;
	}


	//Append zeroes to CRC frame data here.
	for(int k=0; k<8; k++){
		//Append zeroes to dividend
		frameData.add(0); 
	}
	//Remove first byte's worth of data here.
	for(int k=0; k<8; k++){
		frameData.remove();
	}
	
	//Calculate this frame's CRC remainder here.
	while(!frameData.isEmpty()){
		if((0xff & remainder) >= 0b10000000){
			//if the remainder start with 0, XOR it, otherwise move on.
			remainder = (byte)((remainder & 0xff) ^ polynomial);
		}
		//then move right, down the dividend.
		remainder = (byte)(((remainder & 0xff) << 1) + frameData.remove());
	}
	System.out.println("comparing calculated remainder of " + remainder + " to received data's remainder tag, " + receivedRemainder);
		
	if(remainder != receivedRemainder){
		errorDetected = true;
	}else{
		errorDetected = false;
	}
	
	
	if(errorDetected){
		System.out.println("CRC mismatch detected in frame holding data: " + contents + "\n");
	}else{
		System.out.println("no errors found in frame holding data: " + contents + "\n");
	}
	
	return extractedData;

    } // processFrame ()
    // ===============================================================



    // ===============================================================
    private void cleanBufferUpTo (Iterator<Byte> end) {

	Iterator<Byte> i = byteBuffer.iterator();
	while (i.hasNext() && i != end) {
	    i.next();
	    i.remove();
	}

    }
    // ===============================================================



    // ===============================================================
    // DATA MEMBERS
    // ===============================================================


	private byte polynomial = (byte)(0b10000111 & 0xff);
	//Can accept any FOUR bit polynomial.

    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    // ===============================================================

}
