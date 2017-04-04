package thmp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;

import thmp.ParseState.ParseStateBuilder;
import thmp.ParseState.VariableDefinition;
import thmp.ParseState.VariableName;
import thmp.search.CollectThm;
import thmp.search.Searcher;
import thmp.search.ThmSearch;
import thmp.utils.FileUtils;
import thmp.utils.MacrosTrie;
import thmp.utils.WordForms;
import thmp.utils.MacrosTrie.MacrosTrieBuilder;

/**
 * Used to detect hypotheses in a sentence.
 * 
 * Serializes ALL_THM_WORDS_LIST to file, to be used as seed words for next time
 * search is initialized.
 * 
 * @author yihed
 *
 */
public class DetectHypothesis {
	
	private static final Logger logger = LogManager.getLogger(DetectHypothesis.class);
	//pattern matching is faster than calling str.contains() repeatedly 
	//which is O(mn) time.
	private static final Pattern HYP_PATTERN = WordForms.get_HYP_PATTERN();
	//positive look behind to split on any punctuation before a space.
	//private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("(?<=[\\.|;|,|!|:]) ");
	
	//also incorporate the separator pattern from WordForms!
	//deliberately excluding "\\\\", "\\$"
	//Positive look behind, split on empty space preceded by bracket/paren/brace, preceded by non-empty-space
	private static final Pattern SYMBOL_SEPARATOR_PATTERN = Pattern.compile("-|'|\\+|\\s+|(?:(?:)?<=(?:[^\\s](?:[\\(\\[\\{])))|\\)|\\]\\}");
	
	private static final Pattern BRACKET_SEPARATOR_PATTERN = Pattern.compile("([^\\(\\[\\{]+)[\\(\\[\\{].*");
	
	//contains ParsedExpressions, to be serialized to persistent storage
	private static final List<ParsedExpression> parsedExpressionList = new ArrayList<ParsedExpression>();
	private static final List<String> parsedExpressionStrList = new ArrayList<String>();
	private static final List<String> DefinitionListWithThmStrList = new ArrayList<String>();
	private static final List<String> allThmsStrWithSpaceList = new ArrayList<String>();
	
	private static final String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
	private static final String parsedExpressionStringFileStr = "src/thmp/data/parsedExpressionList.txt";
	private static final String parsedExpressionSerialFileNameStr = "parsedExpressionList.dat";
	private static final String parsedExpressionStringFileNameStr = "parsedExpressionList.txt";
	
	private static final String allThmsStringFileStr = "src/thmp/data/allThmsList.txt";
	private static final String allThmsStringFileNameStr = "allThmsList.txt";
	
	//files to serialize theorem words to.
	public static final String allThmWordsMapSerialFileStr = "src/thmp/data/allThmWordsMap.dat";
	private static final String allThmWordsMapStringFileStr = "src/thmp/data/allThmWordsMap.txt";
	public static final String allThmWordsMapSerialFileNameStr = "allThmWordsMap.dat";
	private static final String allThmWordsMapStringFileNameStr = "allThmWordsMap.txt";
	//not used by next runs, but nice to have the list for inspection.
	//private static final String allThmWordsSerialFileStr = "src/thmp/data/allThmWordsList.dat";
	//private static final String allThmWordsStringFileStr = "src/thmp/data/allThmWordsList.txt";
	
	private static final String statsFileStr = "src/thmp/data/parseStats.txt";
	
	//serialize the words as well, to bootstrap up after iterations of processing. The math words are going to 
	//stabilize.
	//This is ordered based on word frequencies.
	private static final List<String> ALL_THM_WORDS_LIST;
	private static final Map<String, Integer> ALL_THM_WORDS_FREQ_MAP;
	
	private static final boolean PARSE_INPUT_VERBOSE = true;
	//whether to gather a list of statistics, such as percentage of thms with full parses, or non-null head ParseStruct's.
	//private static final boolean GATHER_STATS = true;
	
	//pattern for lines to skip any kind of parsing, even hypothesis-detection.
	//skip examples and bibliographies  
	//Pattern.compile("\\\\begin\\{proof\\}.*|\\\\begin\\{exam.*|\\\\begin\\{thebib.*");
	private static final Pattern SKIP_PATTERN = WordForms.getSKIP_PATTERN();	
	////Pattern.compile("\\\\end\\{proof\\}.*|\\\\end\\{exam.*|\\\\end\\{thebib.*")
	private static final Pattern END_SKIP_PATTERN = WordForms.getEND_SKIP_PATTERN();			
	
	//single lines to skip. Such as comments
	//Pattern.compile("^%.*|\\\\begin\\{bib.*")
	private static final Pattern SINGLE_LINE_SKIP_PATTERN = WordForms.getSINGLE_LINE_SKIP_PATTERN();
	
	private static final Pattern END_DOCUMENT_PATTERN = Pattern.compile("\\\\end\\{document\\}.*");
	private static final Pattern NEW_DOCUMENT_PATTERN = Pattern.compile(".*\\\\documentclass.*");
	
	static{
		Searcher.SearchMetaData.set_gatheringDataBoolToTrue();
		FileUtils.set_dataGenerationMode();	
		/*The "next time" form previous time refers to current run in this static initializer.*/
		ALL_THM_WORDS_LIST = new ArrayList<String>(CollectThm.ThmWordsMaps.get_contextVecWordsNextTimeMap().keySet());
		ALL_THM_WORDS_FREQ_MAP = CollectThm.ThmWordsMaps.get_contextVecWordsNextTimeMap();
		//this SHOULD be done at the end! So keep in sync with the others .dat, so don't need to parse everything twice.
		//Then use current list, but wordsList's from previous runs.
		//ThmSearch.TermDocumentMatrix.createTermDocumentMatrixSVD();
	}
	
	public static class Runner{
		
		public static void main(String[] args){
			InputParams inputParams = new InputParams(args);
			if(inputParams.usePreviousDocWordsFreqMaps){
				Searcher.SearchMetaData.set_previousWordDocFreqMapsPath(inputParams.getPathToDocWordsFreqMap());
			}			
			DetectHypothesis.readAndProcessInputData(inputParams);
		}
	}
	
	/**
	 * Statistics class to record statistics such as the percentage of thms 
	 * that have spanning parses and/or non-null headParseStruct's.
	 * Should use -D etc to indicate what type of parameters.
	 */
	public static class Stats{
		//number of thms for which headParseStruct == null
		private int headParseStructNullNum = 0;
		//total number of theorems
		private int totalThmsNum = 0;
		
		public Stats(){
			this.headParseStructNullNum = 0;
			this.totalThmsNum = 0;
		}
		
		public Stats(int numHeadParseStructNull, int numTotalThms){
			this.headParseStructNullNum = numHeadParseStructNull;
			this.totalThmsNum = numTotalThms;
		}
		
		/**
		 * @param headParseStructNullNum the headParseStructNullNum to set
		 */
		public void incrementHeadParseStructNullNum() {
			this.headParseStructNullNum++;
		}

		/**
		 * @param totalThmsNum the totalThmsNum to set
		 */
		public void incrementTotalThmsNum() {
			this.totalThmsNum++;
		}

		/**
		 * @return the headParseStructNullNum
		 */
		public int getHeadParseStructNullNum() {
			return headParseStructNullNum;
		}

		/**
		 * @return the totalThmsNum
		 */
		public int getTotalThmsNum() {
			return totalThmsNum;
		}
		
		/**
		 * @return the totalThmsNum
		 */
		public double getNonNullPercentage() {
			return ((double)headParseStructNullNum)/totalThmsNum;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder("totalThmsNum: ").append(totalThmsNum);
			sb.append(" Percetage nontrivial parseStructHeads: ").append(getNonNullPercentage());
			return sb.toString();
		}
	}
	
	/**
	 * Combination of theorem String and the list of
	 * assumptions needed to define the variables in theorem.
	 */
	public static class DefinitionListWithThm implements Serializable, TheoremContainer {
		
		private static final long serialVersionUID = 7178202892278343033L;
		private String thmStr;		
		//thmStr, with definition strings prepended.
		private String thmWithDefStr;
		//name of source file from which thm is extracted.
		private String srcFileName;
		
		private List<VariableDefinition> definitionList = new ArrayList<VariableDefinition>();
		
		public DefinitionListWithThm(String thmStr, List<VariableDefinition> definitionList,
				String thmWithDefStr, String srcFileName_){
			this.thmStr = thmStr;
			this.definitionList = definitionList;
			this.thmWithDefStr = thmWithDefStr;
			this.srcFileName = srcFileName_;
		}
		
		@Override
		public String toString(){
			//initial capacity should be average number of characters.
			StringBuilder sb = new StringBuilder(250);
			if(null != this.definitionList){
				sb.append("- definitionList: ").append(definitionList);
			}
			if(null != thmStr){
				sb.append("thmStr: -").append(thmStr);
			}
			return sb.toString();
		}

		/**
		 * @return the thmWithDefStr
		 */
		public String getThmWithDefStr() {
			return thmWithDefStr;
		}
		
		/**
		 * Returns String of thm along with definitions.
		 */
		@Override
		public String getEntireThmStr() {
			return thmWithDefStr;
		}
		
		/**
		 * @return the theorem String.
		 */
		public String getThmStr() {
			return thmStr;
		}
		
		/**
		 * @return name of source file from which thm is extracted.
		 */
		public String getSrcFileName() {
			return srcFileName;
		}
		
		/**
		 * @return the definitionList
		 */
		public List<VariableDefinition> getDefinitionList() {
			return definitionList;
		}		
	}
	
	/**
	 * Whether the inputStr is a hypothesis. By checking whether the input 
	 * contains any assumption-indicating words.
	 * @param inputStr
	 * @return
	 */
	public static boolean isHypothesis(String inputStr){
		if(HYP_PATTERN.matcher(inputStr.toLowerCase()).matches()){
			return true;
		}
		return false;
	}
	
	private static class InputParams{
		
		String texFilesDirPath;
		String serializedTexFileNamesFileStr;
		//e.g. "0208_001/0208/ProjectedTDMatrix.mx"
		boolean usePreviousDocWordsFreqMaps;
		String pathToProjectionMx;
		//map of word frequencies.
		String pathToWordFreqMap;
		String parsedExpressionSerialFileStr = DetectHypothesis.parsedExpressionSerialFileStr;
		String allThmWordsMapSerialFileStr = DetectHypothesis.allThmWordsMapSerialFileStr;
		String allThmWordsMapStringFileStr = DetectHypothesis.allThmWordsMapStringFileStr;
		String parsedExpressionStringFileStr = DetectHypothesis.parsedExpressionStringFileStr; //parsedExpressionStrList
		String allThmsStringFileStr = DetectHypothesis.allThmsStringFileStr; //allThmsStrWithSpaceList
		
		//where to put the full dim TD matrix
		String fullTermDocumentMxPath;
		/**
		 * Usual use needs *4* parameters in args. March 2017.
		 * texFilesDirPath, serializedTexFileNamesFileStr, pathToProjectionMx, pathToWordFreqMap.
		 * @param args
		 */
		InputParams(String args[]){
			int argsLen = args.length;
			if(argsLen > 1){
				texFilesDirPath = args[0];
				//inputFile = new File(argsSrcStr);
				serializedTexFileNamesFileStr = args[1];
			
				if(argsLen > 3){
					pathToProjectionMx = args[2];
					pathToWordFreqMap = args[3];
					usePreviousDocWordsFreqMaps = true;
					char fileSeparatorChar = File.separatorChar;
					int texFilesDirPathLen = texFilesDirPath.length();
					if(texFilesDirPath.charAt(texFilesDirPathLen-1) != fileSeparatorChar){
						texFilesDirPath = texFilesDirPath + fileSeparatorChar;
					}
					
					this.parsedExpressionSerialFileStr = texFilesDirPath + DetectHypothesis.parsedExpressionSerialFileNameStr;
					this.allThmWordsMapSerialFileStr = texFilesDirPath + DetectHypothesis.allThmWordsMapSerialFileNameStr;
					this.allThmWordsMapStringFileStr = texFilesDirPath + DetectHypothesis.allThmWordsMapStringFileNameStr;
					this.parsedExpressionStringFileStr = texFilesDirPath + DetectHypothesis.parsedExpressionStringFileNameStr; //parsedExpressionStrList
					this.allThmsStringFileStr = texFilesDirPath + DetectHypothesis.allThmsStringFileNameStr;
					
					//create fullTermDocumentMxPath using base path
					this.fullTermDocumentMxPath = texFilesDirPath + ThmSearch.TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME + ".mx";
					
				}			
			}
		}
		String getPathToProjectionMx(){
			return pathToProjectionMx;
		}
		
		String getPathToDocWordsFreqMap(){
			return pathToWordFreqMap;
		}
	}
	
	/**
	 * only parse if sentence is hypothesis, when parsing outside theorems.
		to build up variableNamesMMap. Also collect the sentence that 
		defines a variable, to include inside the theorem for search.
	 * Normal use is when args is 2-String argument, first is path to dir containing
	 * tex files, second is list of tex files in that dir.
	 * 1st, path to directory containing .tex files. 
	 * 2nd, texFileNamesSerialFileStr, path to file names that contain names of the .tex files 
	 * that should be parsed.
	 * 3rd, path to projection mx.
	 * 4th, path to allThmWordsMap
	 * Should use configuration file!
	 * @param args
	 */
	private static void readAndProcessInputData(//String[] args
			InputParams inputParams) {
		/* read in a directory name, parse all the files individually. */ 		
		//try to read file path from command line argument first
		//path should be absolute
		
		//could be file or dir
		File inputFile = null;
		
		/*absolute path to files and the tar file name they live in*/
		Map<String, String> texFileNamesMap = null;
		String texFileNamesSerialFileStr = inputParams.serializedTexFileNamesFileStr;
		if(texFileNamesSerialFileStr != null){
			//String argsSrcStr = args[0];
			String texFilesDirPath = inputParams.texFilesDirPath;
			inputFile = new File(texFilesDirPath);			
			//String texFileNamesSerialFileStr = inputParams.serializedTexFileNamesFileStr;
			
			//set of *absolute* path names.
			texFileNamesMap = deserializeTexFileNames(texFileNamesSerialFileStr);
			if(null == texFileNamesMap){
				return;
			}
		}
		//resort to default file if no arg supplied
		else{
			//try{
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/CommAlg5.txt"));
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/fieldsRawTex.txt"));
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/samplePaper1.txt"));
				inputFile = new File("src/thmp/data/Total.txt");
				//inputFile = new File("src/thmp/data/thmsFeb26.txt");
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/Total.txt"));
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/fieldsThms2.txt"));
			/*}catch(FileNotFoundException e){
				e.printStackTrace();
				throw new IllegalStateException("Source file not found!");
			}*/
		}		
		List<DefinitionListWithThm> defThmList = new ArrayList<DefinitionListWithThm>();
		Stats stats = new Stats();
		if(inputFile.isDirectory()){
			//if(true) throw new IllegalStateException("input is directory! texFileNamesMap: " + texFileNamesMap);
			//get all filenames from dir. Get tex file names from serialized file data.
			//File[] files = inputFile.listFiles();			
			try{
				for(Map.Entry<String, String> fileNameEntry : texFileNamesMap.entrySet()){
					//this is absolute file path
					String fileName = fileNameEntry.getKey();
					File file = new File(fileName);
					BufferedReader inputBF = null;
					try{
						inputBF = new BufferedReader(new FileReader(file));	
					}catch(FileNotFoundException e){						
						String msg = fileName + " source file not found!";
						System.out.println(msg);
						logger.error(msg);
						continue;
					}
					String tarFileName = fileNameEntry.getValue();
					extractThmsFromFiles(inputBF, defThmList, stats, tarFileName);
					FileUtils.silentClose(inputBF);
				}
			}catch(Throwable e){
				logger.error(e.getStackTrace());			
				throw e;
			}finally{
				//serialize, so don't discard the items already parsed.
				serializeDataToFile(stats, defThmList, inputParams);			
			}
		}else{
			BufferedReader inputBF = null;
			try{
				inputBF = new BufferedReader(new FileReader(inputFile));
			}catch(FileNotFoundException e){
				logger.error(e.getStackTrace());
				throw new IllegalStateException(e);
			}
			//inputBFCreatedBool = true;
			try{
				extractThmsFromFiles(inputBF, defThmList, stats, inputFile.getName());				
			}catch(Throwable e){
				logger.error("Error during thm exptraction and parsing!"+e.getMessage());			
				throw e;
			}finally{
				//serialize, so don't discard the items already parsed.
				serializeDataToFile(stats, defThmList, inputParams);			
			}
		}
		//System.out.println("thmList " +allThmsStrWithSpaceList );
		//deserialize objects
		boolean deserialize = false;
		if(deserialize){
			deserializeParsedExpressionsList();
		}		
		FileUtils.cleanupJVMSession();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> deserializeTexFileNames(String texFileNamesSerialFileStr) {
		return ((List<Map<String, String>>)FileUtils.deserializeListFromFile(texFileNamesSerialFileStr)).get(0);
	}

	/**
	 * @param parseState
	 * @param inputBF
	 * @param fileName name of file, to append to parsed thms.
	 * @throws Throwable
	 */
	private static void extractThmsFromFiles(BufferedReader inputBF, 
			List<DefinitionListWithThm> defThmList, Stats stats, String fileName) {

		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();
		
		try{
			readAndParseThm(inputBF, parseState, defThmList, stats, fileName);		
		}catch(IOException e){
			e.printStackTrace();
			logger.error(e.getStackTrace());
		}
		//System.out.println("DefinitionListWithThm list: " + defThmList);
		System.out.println("STATS -- percentage of non-trivial ParseStruct heads: " + stats.getNonNullPercentage() 
			+ " out of total " + stats.getTotalThmsNum() + "thms");
		DefinitionListWithThmStrList.add(defThmList.toString()+ "\n");
		//why do I need this??
		/*for(DefinitionListWithThm def : defThmList){
			DefinitionList.add(def.getDefinitionList().toString());
		}*/		
		//return stats;
	}

	/**
	 * Serialize collected data to persistent storage.
	 * The serializations done in this method should remain atomic, i.e. do *not* perform 
	 * a subset of steps only, since we rely on the different serialized data to come from
	 * the same source with the same settings.
	 * @param pathToProjectionMx path to  projection mx, if specified.
	 */
	private static void serializeDataToFile(Stats stats, List<DefinitionListWithThm> defThmList,
			InputParams inputParams) {
		//List<Object> listToSerialize = new ArrayList<Object>();
		//listToSerialize.add(parsedExpressionList);
		
		String pathToProjectionMx = inputParams.getPathToProjectionMx();
		String pathToWordFreqMap = inputParams.pathToWordFreqMap;
		String fullTermDocumentMxPath = inputParams.fullTermDocumentMxPath;
		String parsedExpressionSerialFileStr = inputParams.parsedExpressionSerialFileStr;
		String allThmWordsMapSerialFileStr = inputParams.allThmWordsMapSerialFileStr;
		String allThmWordsMapStringFileStr = inputParams.allThmWordsMapStringFileStr;
		String parsedExpressionStringFileStr = inputParams.parsedExpressionStringFileStr;
		String allThmsStringFileStr = inputParams.allThmsStringFileStr;
		//inputParams.allThmWordsStringFileStr;
		
		boolean projectionPathsNotNull = (null != pathToProjectionMx && null != pathToWordFreqMap);
		
		logger.info("Serializing parsedExpressionList, etc, to file...");
		try{
			FileUtils.serializeObjToFile(parsedExpressionList, parsedExpressionSerialFileStr);		
			//serialize words used for context vecs
			//FileUtils.serializeObjToFile(ALL_THM_WORDS_LIST, allThmWordsSerialFileStr);
			
			List<Map<String, Integer>> wordMapToSerializeList = new ArrayList<Map<String, Integer>>();
			wordMapToSerializeList.add(ALL_THM_WORDS_FREQ_MAP);
			FileUtils.serializeObjToFile(wordMapToSerializeList, allThmWordsMapSerialFileStr);
			//this list is for human inspection.
			List<String> wordMapStringList = new ArrayList<String>();
			wordMapStringList.add(ALL_THM_WORDS_FREQ_MAP.toString());
			FileUtils.writeToFile(wordMapStringList, allThmWordsMapStringFileStr);
			
			//write parsedExpressionList to file
			FileUtils.writeToFile(parsedExpressionStrList, parsedExpressionStringFileStr);
			
			//write just the thms
			FileUtils.writeToFile(allThmsStrWithSpaceList, allThmsStringFileStr);
			//append to stats file!
			FileUtils.appendObjToFile(stats, statsFileStr);
			//FileUtils.writeToFile(ALL_THM_WORDS_LIST, allThmWordsStringFileStr);
		}catch(Throwable e){
			logger.error("Error occurred when writing and serializing to file! " + e.getMessage());
			throw e;
		}
		logger.info("Done serializing parsedExpressionList & co to files! Beginning to compute SVD for parsedExpressionList thms.");
		
		/* Creates the term document matrix, and serializes to .mx file.
		 * If this step fails, need to re-run to produce matrix. This should run at end of this method,
		 * so others have already serialized in case this fails.*/
		ImmutableList<TheoremContainer> immutableDefThmList = ImmutableList.copyOf(defThmList);
		if(projectionPathsNotNull){
			Map<String, Integer> wordFreqMap = getWordFreqMap(pathToWordFreqMap);
			//first serialize full dimensional TD mx, then project using provided projection mx.
			ThmSearch.TermDocumentMatrix.serializeHighDimensionalTDMx(immutableDefThmList, fullTermDocumentMxPath, wordFreqMap);
			//replace the last bit of the path with the name of the reduced mx.
			String pathToReducedDimTDMx = replaceFullTDMxName(fullTermDocumentMxPath, ThmSearch.TermDocumentMatrix.PROJECTED_MX_NAME);
			ThmSearch.TermDocumentMatrix.projectTermDocumentMatrix(fullTermDocumentMxPath, pathToProjectionMx, 
					pathToReducedDimTDMx);
		}else{
			ThmSearch.TermDocumentMatrix.createTermDocumentMatrixSVD(immutableDefThmList);						
		}
	}
	
	private static String replaceFullTDMxName(String fullTermDocumentMxPath, String projectedMxName){
		char separatorChar = File.separatorChar;
		int i = fullTermDocumentMxPath.length() - 1;
		while(i > -1 && fullTermDocumentMxPath.charAt(i) != separatorChar){
			i--;
		}
		if(i == -1){
			return projectedMxName;
		}
		return fullTermDocumentMxPath.substring(0, i+1) + projectedMxName + ".mx";		
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Integer> getWordFreqMap(String pathToWordFreqMap){
		Map<String, Integer> wordFreqMap = ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(pathToWordFreqMap)).get(0);
		return wordFreqMap;
	}
	
	/**
	 * Deserialize objects in parsedExpressionOutputFileStr, so we don't 
	 * need to read and parse through all papers on every server initialization.
	 * Can just read from serialized data.
	 */
	@SuppressWarnings("unchecked")
	private static List<ParsedExpression> deserializeParsedExpressionsList(){
	
		List<ParsedExpression> parsedExpressionsList = null;
		FileInputStream fileInputStream = null;
		ObjectInputStream objectInputStream = null;
		try{
			fileInputStream = new FileInputStream(parsedExpressionSerialFileStr);
			objectInputStream = new ObjectInputStream(fileInputStream);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ParsedExpressionList output file not found!");
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while opening ObjectOutputStream");
		}
		
		try{
			Object o = objectInputStream.readObject();
			parsedExpressionsList = (List<ParsedExpression>)o;		
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading deserialized data!");
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ClassNotFoundException while writing to file or closing resources");
		}finally{
			try{
				objectInputStream.close();
				fileInputStream.close();
			}catch(IOException e){
				e.printStackTrace();
				throw new IllegalStateException("IOException while closing resources");
			}
		}
		return parsedExpressionsList;
	}
	
	/**
	 * Extracts list of theorems/propositions/etc from provided BufferedReader,
	 * with hypotheses added. 
	 * @param srcFileReader
	 *            BufferedReader to get tex from.
	 * @param thmWebDisplayList
	 *            List to contain theorems to display for the web. without
	 *            \labels, \index, etc. Can be null, for callers who don't need it.
	 * @param bareThmList
	 * 				bareThmList for parsing, without label content. Can be null.
	 * @param macros author-defined macros using \newtheorem
	 * @return List of unprocessed theorems read in from srcFileReader, for bag
	 *         of words search.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void readAndParseThm(BufferedReader srcFileReader, 
			ParseState parseState, List<DefinitionListWithThm> definitionListWithThmList,
			Stats stats, String fileName) throws IOException{
		
		//Pattern thmStartPattern = ThmInput.THM_START_PATTERN;
		//Pattern thmEndPattern = ThmInput.THM_END_PATTERN;
		List<String> customBeginThmList = new ArrayList<String>();
		
		MacrosTrieBuilder macrosTrieBuilder = new MacrosTrieBuilder();
		//contextual sentences outside of theorems, to be scanned for
		//definitions, and parse those definitions. Reset between theorems.
		StringBuilder contextSB = new StringBuilder();
		
		//List<DefinitionListWithThm> definitionListWithThmList = new ArrayList<DefinitionListWithThm>();		
		String line = extractMacros(srcFileReader, customBeginThmList, macrosTrieBuilder);
		MacrosTrie macrosTrie = macrosTrieBuilder.build();
		
		//append list of macros to THM_START_STR and THM_END_STR
		Pattern[] customPatternAr = addMacrosToThmBeginEndPatterns(customBeginThmList);
		
		Pattern thmStartPattern = customPatternAr[0];
		Pattern thmEndPattern = customPatternAr[1];	
		Pattern eliminateBeginEndThmPattern = customPatternAr[2];
		
		StringBuilder newThmSB = new StringBuilder();
		Matcher matcher;
		boolean inThm = false;
		
		if(null != line){
			matcher = thmStartPattern.matcher(line);
			//use find(), not matches(), to look for any matching substring
			if (matcher.find()) {			
				inThm = true;
				parseState.setInThmFlag(true);
			}
		}
		while ((line = srcFileReader.readLine()) != null) {
			if (WordForms.getWhiteEmptySpacePattern().matcher(line).matches()){
				continue;
			}
			
			if(SINGLE_LINE_SKIP_PATTERN.matcher(line).matches()){
				continue;
			}
			//should skip certain sections, e.g. \begin{proof}
			Matcher skipMatcher = SKIP_PATTERN.matcher(line);
			if(skipMatcher.find()){
				while ((line = srcFileReader.readLine()) != null){
					if(END_SKIP_PATTERN.matcher(line).find()){						
						break;
					}
				}
				continue;
			}
			
			boolean appendedToThm = false;
			matcher = thmStartPattern.matcher(line);
			if (matcher.matches()) {				
				// process here, return two versions, one for bag of words, one
				// for display
				// strip \df, \empf. Index followed by % strip, not percent
				// don't strip.
				// replace enumerate and \item with *
				//thmWebDisplayList, and bareThmList should both be null
				String contextStr = ThmInput.removeTexMarkup(contextSB.toString(), null, null, macrosTrie,
						eliminateBeginEndThmPattern);
				
				//scan contextSB for assumptions and definitions
				//and parse the definitions
				detectAndParseHypothesis(contextStr, parseState, stats);	
				
				inThm = true;
				newThmSB.append(line);
				appendedToThm = true;
				//this should be set *after* calling detectAndParseHypothesis(), since detectAndParseHypothesis
				//depends on the state.
				parseState.setInThmFlag(true);
				contextSB.setLength(0);
			}
			//if and not else if, since \begin{} and \end{} could be on same line.
			if (thmEndPattern.matcher(line).matches()) {
				
				inThm = false;				
				if(0 == newThmSB.length()){
					continue;
				}
				//System.out.println("newThmSB: " + newThmSB);
				//System.out.println("!---------! line: " + line+" thmEndPattern: " + thmEndPattern);
				
				//Need to read in until \end{cor} etc
				newThmSB.append(" ").append(line);
				
				//parse hyp and thm. BE SURE TO ONLY RE-ORDER WORDS based on word freuqency at last run, before serializing!
				String thm = processParseHypThm(newThmSB, parseState, stats, definitionListWithThmList, fileName, macrosTrie,
						eliminateBeginEndThmPattern);
				allThmsStrWithSpaceList.add(thm + "\n\n");
				
				//with hyps
				//allThmHypStrList.add();
				continue;
			}else if(END_DOCUMENT_PATTERN.matcher(line).matches()){
				parseState.parseRunGlobalCleanUp();
				//read until a new document, marked by \documentclass..., is encountered.
				//sometimes the \documentclass follows immediately after \end{document},
				//without starting a new line.
				if(!NEW_DOCUMENT_PATTERN.matcher(line).matches()){
					while(null != (line = srcFileReader.readLine())){					
						if(NEW_DOCUMENT_PATTERN.matcher(line).matches()){
							break;
						}					
					}
				}
				//If multiple latex documents gathered together in one file.				
				macrosTrieBuilder = new MacrosTrieBuilder();
				customBeginThmList = new ArrayList<String>();
				line = extractMacros(srcFileReader, customBeginThmList, macrosTrieBuilder);
				macrosTrie = macrosTrieBuilder.build();
				//append list of macros to THM_START_STR and THM_END_STR
				customPatternAr = addMacrosToThmBeginEndPatterns(customBeginThmList);				
				continue;
			}

			if(inThm && !appendedToThm){
				newThmSB.append(" ").append(line);
			}else if (!inThm) {
				//need to parse to gather definitions
				//add to contextSB
				contextSB.append(" ").append(line);
			}
		}
		parseState.writeUnknownWordsToFile();		
	}

	/**
	 * Processes (e.g. remove tex markup) and parses a theorem after it has been read in.
	 * @param newThmSB StringBuilder containing the theorem.
	 * @param parseState
	 * @param stats
	 * @param definitionListWithThmList
	 */
	private static String processParseHypThm(StringBuilder newThmSB, ParseState parseState, Stats stats, 
			List<DefinitionListWithThm> definitionListWithThmList, String srcFileName, MacrosTrie macrosTrie,
			Pattern eliminateBeginEndThmPattern){
		
		// process here, return two versions, one for bag of words, one
		// for display
		// strip \df, \empf. Index followed by % strip, not percent
		// don't strip.
		// replace enumerate and \item with *
		//thmWebDisplayList, and bareThmList should both be null
		//String contextStr = ThmInput.removeTexMarkup(contextSB.toString(), null, null, macrosTrie);
		String thm = ThmInput.removeTexMarkup(newThmSB.toString(), null, null, macrosTrie,
				eliminateBeginEndThmPattern);
		
		//Must clear headParseStruct and curParseStruct of parseState, so newThm
		//has its own stand-alone parse tree.
		parseState.setCurParseStruct(null);
		parseState.setHeadParseStruct(null);
		
		//first gather hypotheses in the theorem. <--Note that this will cause the hypothetical
		//sentences to be parsed twice, unless these sentences are marked so they don't get parsed again.
		detectAndParseHypothesis(thm, parseState, stats);
		//if(true) throw new IllegalStateException(parseState.toString());
		//if contained in local map, should be careful about when to append map.
		
		//append to newThmSB additional hypotheses that are applicable to the theorem.				
		DefinitionListWithThm thmDef = appendHypothesesAndParseThm(thm, parseState, stats, srcFileName);
		
		definitionListWithThmList.add(thmDef);
		//System.out.println("___-------++++++++++++++" + thmDef);
		//should parse the theorem.
		//serialize the full parse, i.e. parsedExpression object, along with original input.				
		
		/*if (!WordForms.getWhitespacePattern().matcher(thm).find()) {
			thms.add(thm);
		}*/
		//local clean up, after done with a theorem, but still within same document.
		parseState.parseRunLocalCleanUp();
		newThmSB.setLength(0);
		return thm;
	}
	
	/**
	 * Create custom start and end patterns by appending to THM_START_STR and THM_END_STR.
	 * @param macrosList
	 * @return
	 */
	private static Pattern[] addMacrosToThmBeginEndPatterns(List<String> macrosList) {
		//compiler will inline these, so don't add function calls to stack.
		Pattern[] customPatternAr = new Pattern[]{ThmInput.THM_START_PATTERN, ThmInput.THM_END_PATTERN,
				ThmInput.ELIMINATE_BEGIN_END_THM_PATTERN};
		if(!macrosList.isEmpty()){
			StringBuilder startBuilder = new StringBuilder();
			StringBuilder endBuilder = new StringBuilder();
			StringBuilder eliminateBuilder = new StringBuilder();
			for(String macro : macrosList){
				//create start and end macros  .*\\\\begin\\s*\\{def(?:.*)
				//but only if isn't already included in THM_START_PATTERN \\\\begin\\{def(?:[^}]*)\\}\\s*
				if(ThmInput.THM_START_PATTERN.matcher("\\begin{" + macro).matches()){
					continue;
				}
				startBuilder.append("|.*\\\\begin\\s*\\{").append(macro).append(".*");				
				endBuilder.append("|.*\\\\end\\s*\\{").append(macro).append(".*");
				eliminateBuilder.append("|\\\\begin\\s*\\{").append(macro).append("\\}\\s*");
				eliminateBuilder.append("|\\\\end\\s*\\{").append(macro).append("\\}\\s*");
			}
			if(startBuilder.length() > 0){
				customPatternAr[0] = Pattern.compile(ThmInput.THM_START_STR + startBuilder);
				customPatternAr[1] = Pattern.compile(ThmInput.THM_END_STR + endBuilder);
				customPatternAr[2] = Pattern.compile(ThmInput.ELIMINATE_BEGIN_END_THM_STR + endBuilder, 
						Pattern.CASE_INSENSITIVE);
			}
		}
		//if(true) throw new IllegalStateException("updated macros list: " + Arrays.deepToString(customPatternAr));
		return customPatternAr;
	}

	/**
	 * Read in custom macros, break as soon as \begin{...} encountered, 
	 * in particular \begin{document}. There are no \begin{...} in the preamble
	 * @param srcFileReader
	 * @param thmMacrosList *Only* macros to indicate begin of new theorems, propositions, etc.
	 * @throws IOException
	 */
	private static String extractMacros(BufferedReader srcFileReader, List<String> thmMacrosList, 
			MacrosTrieBuilder macrosTrieBuilder) throws IOException {
		
		String line = null;		
		while ((line = srcFileReader.readLine()) != null) {
			//should also extract those defined with \def{} patterns.
			Matcher newThmMatcher;	
			
			if(ThmInput.BEGIN_PATTERN.matcher(line).matches()){
				break;
			}else if((newThmMatcher = ThmInput.NEW_THM_PATTERN.matcher(line)).matches()){
				//should be a proposition, hypothesis, etc. E.g. don't look through proofs.
				if(ThmInput.THM_TERMS_PATTERN.matcher(newThmMatcher.group(2)).matches()){
					thmMacrosList.add(newThmMatcher.group(1));	
				}
			}else if((newThmMatcher = ThmInput.NEW_THM_PATTERN2.matcher(line)).matches()){
				String commandStr = newThmMatcher.group(1);
				String replacementStr = newThmMatcher.group(3);
				String slotCountStr = newThmMatcher.group(2);
				int slotCount = null == slotCountStr ? 0 : Integer.valueOf(slotCountStr);
				macrosTrieBuilder.addTrieNode(commandStr, replacementStr, slotCount);
			}else if((newThmMatcher = ThmInput.NEW_THM_PATTERN3.matcher(line)).matches()){
				//case of \def\X{{\cal X}}
				String commandStr = newThmMatcher.group(1);
				String replacementStr = newThmMatcher.group(2);
				macrosTrieBuilder.addTrieNode(commandStr, replacementStr, 0);
			}		
		}
		//if(true)throw new IllegalStateException("macros: "+macrosList);
		return line;
	}
	
	/**
	 * detect hypotheses and definitions, and add definitions to parseState.
	 * @param contextSB
	 * @param parseState
	 */
	private static void detectAndParseHypothesis(String contextStr, ParseState parseState, Stats stats){
		
		//split on punctuations precede a space, but keep the punctuation.
		//String[] contextStrAr = PUNCTUATION_PATTERN.split(contextStr);
		String[] contextStrAr = ThmP1.preprocess(contextStr);
		for(int i = 0; i < contextStrAr.length; i++){
			String sentence = contextStrAr[i];
			if(isHypothesis(sentence)){	
				System.out.println("isHypothesis! " + sentence);
				//if(true) throw new IllegalStateException(sentence);				
				parseState.setCurParseStruct(null);
				parseState.setHeadParseStruct(null);
				ParseRun.parseInput(sentence, parseState, PARSE_INPUT_VERBOSE, stats);
			}
		}
	}
	
	/**
	 * Append hypotheses and definition statements in front of thmSB, 
	 * for the variables that do appear in thmSB.
	 * 
	 * @param thmSB
	 * @param parseState
	 */
	private static DefinitionListWithThm appendHypothesesAndParseThm(String thmStr, ParseState parseState, 
			Stats stats, String srcFileName){
		
		//ListMultimap<VariableName, VariableDefinition> variableNamesMMap = parseState.getGlobalVariableNamesMMap();
		//String thmStr = thmSB.toString();
		StringBuilder thmWithDefSB = new StringBuilder();		
		StringBuilder latexExpr = new StringBuilder();
		
		List<VariableDefinition> variableDefinitionList = new ArrayList<VariableDefinition>();
		//varDefSet set to keep track of which VariableDefinition's have been added, so not to 
		//add duplicate ones.
		Set<VariableDefinition> varDefSet = new HashSet<VariableDefinition>();
		
		int thmStrLen = thmStr.length();		
		boolean mathMode = false;
		
		//filter through text and try to pick up definitions.
		for(int i = 0; i < thmStrLen; i++){
			
			char curChar = thmStr.charAt(i);			
			//go through thm, get the variables that need to be defined
			//once inside Latex, use delimiters, should also take into account
			//the case of entering math mode with \[ !
			if(curChar == '$'){
				if(!mathMode){
					mathMode = true;					
				}else{
					mathMode = false;
					//process the latexExpr, first pick out the variables,
					//and try to find definitions for them. Appends original
					//definition strings to thmWithDefSB. Should only append
					//variables that are not defined within the same thm.				
					List<VariableDefinition> varDefList = pickOutVariables(latexExpr.toString(), //variableNamesMMap,
							parseState, varDefSet, thmWithDefSB);
					
					variableDefinitionList.addAll(varDefList);
					latexExpr.setLength(0);
				}			
			}else if(mathMode){
				latexExpr.append(curChar);
			}			
		}

		//Parse the thm first, with the variableNamesMMap already updated to include contexual definitions.
		//should return parsedExpression object, and serialize it. But only pick up definitions that are 
		//not defined locally within this theorem.
		System.out.println("~~~~~~parsing~~~~~~~~~~");		
		ParseRun.parseInput(thmStr, parseState, PARSE_INPUT_VERBOSE, stats);
		System.out.println("~~~~~~Done parsing~~~~~~~");		
				
		//System.out.println("Adding " + thmWithDefSB + " to theorem " + thmStr);
		
		thmWithDefSB.append(thmStr);
		DefinitionListWithThm defListWithThm = 
				new DefinitionListWithThm(thmStr, variableDefinitionList, thmWithDefSB.toString(), srcFileName);
		
		//relational and context vecs can't be null, since ImmutableList cannot contain null elements
		BigInteger relationalContextVec = parseState.getRelationalContextVec();
		if(null == relationalContextVec){
			//write placeholder
			relationalContextVec = new BigInteger(1, new byte[1]);
		}
		int[] combinedContextVec = parseState.getCurThmCombinedContextVec();
		if(null == combinedContextVec){
			combinedContextVec = ParseState.PLACEHOLDER_CONTEXT_VEC();
		}
		
		//create parsedExpression to serialize to persistent storage to be used later
		//for search, etc
		ParsedExpression parsedExpression = new ParsedExpression(thmStr, parseState.getHeadParseStruct(),
						defListWithThm, combinedContextVec, relationalContextVec);
		
		parsedExpressionList.add(parsedExpression);
		parsedExpressionStrList.add(parsedExpression.toString());
		//return this to supply to search later
		return defListWithThm;
	}
	
	/**
	 * Picks out variables to be defined, and try to match them with prior definitions.
	 * Picks up variable definitions.
	 * @param latexExpr 
	 * @param thmDefSB StringBuilder that's the original input string appended
	 * to the definition strings.
	 * @param varDefSet set to keep track of which VariableDefinition's have been added, so not to 
	 * add duplicate ones.
	 */
	private static List<VariableDefinition> pickOutVariables(String latexExpr, 
			//ListMultimap<VariableName, VariableDefinition> variableNamesMMap,
			ParseState parseState, Set<VariableDefinition> varDefSet,
			StringBuilder thmDefSB){
		
		//list of definitions needed in this latexExpr
		List<VariableDefinition> varDefList = new ArrayList<VariableDefinition>();
		
		//split the latexExpr with delimiters
		String[] latexExprAr = SYMBOL_SEPARATOR_PATTERN.split(latexExpr);
		//System.out.println("=++++++++========= latexExpr " + latexExpr);
		for(int i = 0; i < latexExprAr.length; i++){
			
			String possibleVar = latexExprAr[i];
			//System.out.println("^^^$^%%% &^^^ possibleVar: "+ possibleVar);
			
			//Get a variableName and check if a variable has been defined.
			VariableName possibleVariableName = ParseState.createVariableName(possibleVar);
			VariableDefinition possibleVarDef = new VariableDefinition(possibleVariableName, null, null);
			
			boolean isLocalVar = parseState.getVariableDefinitionFromName(possibleVarDef);
			//whether the variable definition was defined locally in the theorem, used to determine whether
			//to include originalDefiningSentence.
			
			//System.out.println("^^^ variableNamesMMap: "+ variableNamesMMap);
			//System.out.println("^^^^^^^PossibleVar: " + possibleVar);
			//get the latest definition
			//int possibleVarDefListLen = possibleVarDefList.size();
			//if empty, check to see if bracket pattern, if so, check just the name without the brackets.
			//e.g. x in x(yz)
			if(null == possibleVarDef.getDefiningStruct()){
				Matcher bracketSeparatorMatcher = BRACKET_SEPARATOR_PATTERN.matcher(possibleVar);
				if(bracketSeparatorMatcher.find()){
					possibleVariableName = ParseState.createVariableName(bracketSeparatorMatcher.group(1));
					possibleVarDef.setVariableName(possibleVariableName);
					isLocalVar = parseState.getVariableDefinitionFromName(possibleVarDef);				
				}
			}
			//if some variable found.
			if(!isLocalVar && null != possibleVarDef.getDefiningStruct()){			
				if(!varDefSet.contains(possibleVarDef)){
					varDefSet.add(possibleVarDef);
					varDefList.add(possibleVarDef);
					//System.out.println("latestVarDef.getOriginalDefinitionStr() " + latestVarDef.getOriginalDefinitionStr());
			 		thmDefSB.append(possibleVarDef.getOriginalDefinitionSentence()).append(" ");
				}
			}			
		}
		//if(true) throw new IllegalStateException("latexExpr containing var: " + latexExpr + " varDefList " + varDefList);
		return varDefList;
	}
	
}
