import java.util.PriorityQueue;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	/**
	 * Constuctor for HuffProcessor
	 */
	public HuffProcessor() {
		this(0);
	}

	/**
	 *  Constuctor for HuffProcessor
	 * @param debug
	 */
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);

		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}



	/**
	 * Write the magic number and the tree to the beginning/header of the compressed file
	 * 
	 * @param root HuffNode root
	 * @param out Buffered bit stream writing to the output file.
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft==null&&root.myRight==null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
		else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}



	/**
	 * Read the file again and write the encoding for each eight-bit chunk, followed by the encoding for PSEUDO_EOF, then close the file being written 
	 * @param codings String array
	 * @param in Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int value=in.readBits(BITS_PER_WORD);
			if (value==-1) {break;}
			String s= codings[value];
			out.writeBits(s.length(), Integer.parseInt(s,2));
		}
		String end=codings[PSEUDO_EOF];
		out.writeBits(end.length(), Integer.parseInt(end,2));
		}

	/**
	 * Determine the frequency of every eight-bit character/chunk in the file being compressed
	 * @param in Buffered bit stream of the file to be compressed.
	 * @return  integer array that can store 257 values filled with freq and corresponding character
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int [ALPH_SIZE + 1];
		while(true){
			int ch = in.readBits(BITS_PER_WORD);
			if(ch == -1)
				break;
			freq[ch]++;
		}
		freq[PSEUDO_EOF] = 1;
		in.reset();
		return freq;
	}

	/**
	 *From the frequencies, create the Huffman trie/tree used to create encodings
	 * @param counts 
	 * @return HuffNode Tree
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i<counts.length; i++) {  //for every index such that freq[index]>0
			if (counts[i]==0) {continue;}
			pq.add(new HuffNode(i,counts[i],null,null));
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		return pq.remove();
	}

	/**
	 * From the trie/tree, create the encodings for each eight-bit character chun
	 * @param root HuffNode root
	 * @return encodings  String array of 1s and 0s represent bit value
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}

	/**
	 * Helper method for previous method
	 * 
	 * @param root
	 * @param path
	 * @param encodings String array of 1s and 0s represent bit value
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft==null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		else {
			codingHelper(root.myLeft,path+"0",encodings);
			codingHelper(root.myRight,path+"1",encodings);
		}

	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}

	/**
	 * Read the bits from the compressed file and use them to traverse root-to-leaf paths, 
	 * writing leaf values to the output file. Stop when finding PSEUDO_EOF.
	 *  
	 * @param root HuffNode root
	 * @param out Buffered bit stream writing to the output file.
	 * @param in Buffered bit stream of the file to be compressed.
	 * @throws HuffException if bit value is negative
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; 
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else { 
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) 
						break; 
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; 
					}
				}
			}
		}


	}



	/**
	 * Read the tree used to decompress, 
	 * this is the same tree that was used to compress, i.e., was written during compression
	 *  
	 * @param in Buffered bit stream of the file to be compressed.
	 * @throws HuffException if bit value is negative
	 * @return new HuffNode
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("illegal header starts with "+bit);
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}

	}
}