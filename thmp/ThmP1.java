package thmp;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.Struct.Article;
import thmp.Struct.NodeType;
import thmp.search.NGramSearch;
import thmp.search.ThreeGramSearch;
import thmp.search.TriggerMathThm2;
import thmp.utils.WordForms;
import thmp.utils.WordForms.TokenType;

/* 
 * contains hashtable of entities (keys) and properties (values)
 */

public class ThmP1 {

	// should all be StructH's, since these are ent's. 
	// make into multimap, so to get wider scope??
	private static final ListMultimap<String, Struct> variableNamesMap;
	// private static HashMap<String, ArrayList<String>> entityMap =
	// Maps.entityMap;
	// map of structures, for all, disj, etc
	private static final Multimap<String, Rule> structMap;
	private static final Map<String, String> anchorMap;
	// parts of speech map, e.g. "open", "adj"
	private static final ListMultimap<String, String> posMMap;
	// fluff words, e.g. "the", "a"
	private static final Map<String, String> fluffMap;

	private static final Map<String, String> mathObjMap;
	// map for composite adjectives, eg positive semidefinite
	// value is regex string to be matched
	//private static final Map<String, String> adjMap;
	private static final Map<String, Double> probMap;
	// split a sentence into parts, separated by commas, semicolons etc
	// private String[] subSentences;
	//pattern for matching negative of adjectives: "un..."
	private static final Pattern NEGATIVE_ADJECTIVE_PATTERN = Pattern.compile("un(.+)");
	private static final Pattern AND_OR_PATTERN = Pattern.compile("and|or");
	
	// list of parts of speech, ent, verb etc <--should make immutable
	private static final List<String> posList;
	
	// fluff type, skip when adding to parsed ArrayList
	private static final String FLUFF = "Fluff";
	//private static final Pattern ARTICLE_PATTERN = Pattern.compile("a|the|an");
	
	// private static final File unknownWordsFile;
	private static final Path unknownWordsFile = Paths.get("src/thmp/data/unknownWords1.txt");
	private static final Path parsedExprFile = Paths.get("src/thmp/data/parsedExpr.txt");

	private static final List<String> unknownWords = new ArrayList<String>();
	private static List<ParsedPair> parsedExpr = new ArrayList<ParsedPair>();
	
	private static final ImmutableListMultimap<String, FixedPhrase> fixedPhraseMap;	
	
	//private static final Map<String, Integer> twoGramMap = NGramSearch.get2GramsMap();
	//private static final Map<String, Integer> threeGramMap = ThreeGramSearch.get3GramsMap();
	
	/**
	 * List of Stringified Map of parts used to build up a theorem/def etc.  
	 * Global variable, so to be able to pass to other functions.
	 * Not final, since it needs to be cleared and reassigned. 
	 */
	private static List<String> parseStructMapList = new ArrayList<String>();
	//the non-stringified version of parseStructMapList
	private static List<Multimap<ParseStructType, ParsedPair>> parseStructMaps = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
	
	//list of context vectors of the highest-scoring parse tree for each input.
	//will be cleared every time this list is retrieved, which should be once per 
	//parse. Default values of context vec entry is the average val over all context vecs.
	//For Nearest[] to work. *Not* final because need reassignment.
	private static final int parseContextVectorSz;
	//this is cumulative, should be cleared per parse!
	private static int[] parseContextVector; 
	//private static int[] parseContextVector;
	//private static int parseContextVectorSz;
	//list of context vectors, need list instead of single vec, for non-spanning parses
	//private static List<int[]> parseContextVectorList = new ArrayList<int[]>();
	//private static final boolean PROCESS_NGRAM = false;
	
	//private static List<String> contextVectorList = new ArrayList<String>();	
	
	// part of speech, last resort after looking up entity property maps
	// private static HashMap<String, String> pos;
	
	static{
		//should not rely on this check! fix Maps.java initialization
		/*if(Maps.fixedPhraseMap() == null){
			try {
				Maps.readLexicon();
				Maps.readFixedPhrases();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}*/
		/*Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/		
		fluffMap = Maps.BuildMaps.fluffMap;
		mathObjMap = Maps.BuildMaps.mathObjMap;
		fixedPhraseMap = Maps.fixedPhraseMap();
		structMap = Maps.structMap();
		anchorMap = Maps.anchorMap();
		posMMap = Maps.posMMap();		
		//adjMap = Maps.adjMap;
		probMap = Maps.probMap();
		posList = Maps.posList;
		// list of given names, like F in "field F", for bookkeeping later
		// hashmap contains <name, entity> pairs
		// need to incorporate multi-letter names, like sigma
		variableNamesMap = ArrayListMultimap.create();

		parseContextVectorSz = TriggerMathThm2.keywordDictSize();
		parseContextVector = new int[parseContextVectorSz];
	}
	
	/**
	 * Class consisting of a parsed String and its score
	 */
	public static class ParsedPair{
		private String parsedStr;
		private double score;
		//long form or WL-like expr. Useful for "under the hood"
		//can be "long" or "wl". Should use an enum.
		private String form;
		//parsedExprSz used to group parse components together 
		//when full parse is unavailable
		//private int counter;
		//number of units in this parse, as in numUnits (leaf nodes) in Class Struct
		private int numUnits;
		//the commandNumUnits associated to a WLCommand that gives this parsedStr.
		private int commandNumUnits;
		//the ParseStructType of this parsedStr, eg "STM", "HYP", etc.
		private ParseStructType parseStructType;
		private String stringForm;
		
		public ParsedPair(String parsedStr, double score, String form){
			this(parsedStr, score, form, true);
		}
		//toStringForm, True means final form, toString here, false otherwise
		private ParsedPair(String parsedStr, double score, String form, boolean toStringForm){
			this.parsedStr = parsedStr;
			this.score = score;
			this.form = form;
			//toString has to be within several constructor, because GSON needs the private field.
			if(toStringForm){
				this.stringForm = this.toString();
			}
		}
		
		public ParsedPair(String parsedStr, double score, int numUnits, int commandNumUnits){
			this(parsedStr, score, "", false);
			this.numUnits = numUnits;
			this.commandNumUnits = commandNumUnits;
			this.stringForm = this.toString();
		}
		
		/**
		 * Used in case of no WLCommand parse string, then use alternate measure
		 * of span, deduced from longForm dfs.
		 * @param score
		 * @param numUnits
		 * @param commandNumUnits
		 */
		public ParsedPair(double score, int numUnits, int commandNumUnits){
			this("", score, numUnits, commandNumUnits);
		}
		
		public ParsedPair(String parsedStr, double score, int numUnits, int commandNumUnits, ParseStructType type){
			this(parsedStr, score, numUnits, commandNumUnits);
			this.parseStructType = type;
			this.stringForm = this.toString();
		}
		
		public String parsedStr(){
			return this.parsedStr;
		}
		
		public double score(){
			return this.score;
		}

		/**
		 * Number of units covered in this parse.
		 * i.e. measures how consolidated the parse is.
		 * Different from commandNumUnits in that it doesn't count
		 * children of StructH, among other things.
		 * Lower numUnits is better.
		 */
		public int numUnits(){
			return numUnits;
		}

		/**
		 * commandNumUnits of the WLCommands involved in this parse.
		 * I.e. number of leaf nodes covered. Higher is better.
		 * If no WLCommand triggered, measures span of longform.
		 * @return
		 */
		public int commandNumUnits(){
			return commandNumUnits;
		}

		public String form(){
			return this.form;
		}
		
		/**
		 * The toString of this ParsedPair.
		 * Used by presenting gson on web form. 
		 * @return
		 */
		public String stringForm(){
			return this.stringForm;
		}
		
		@Override
		public String toString(){
			String numUnitsString = numUnits == 0 ? "" : "  " + String.valueOf(this.numUnits);
			numUnitsString += commandNumUnits == 0 ? "" : "  " + String.valueOf(this.commandNumUnits);
			if(parseStructType != null){
				return parseStructType + " :> [" + this.parsedStr + " " + String.valueOf(score) + numUnitsString + "]";
			}else{
				return this.parsedStr + " " + String.valueOf(score) + numUnitsString;
			}
		}
	}

	//check in 2- and 3-gram maps. Then determine the type based on 
	//last word, e.g. "regular local ring"
	//check 3-gram first. 
	private static int gatherTwoThreeGram(int i, String[] str, List<Pair> pairs, List<Integer> mathIndexList){
		String curWord = str[i];
		String middleWord = str[i+1];
		String nextWord = middleWord;
		String nextWordSingular = WordForms.getSingularForm(nextWord);
		String twoGram = curWord + " " + nextWord;
		String twoGramSingular = curWord + " " + nextWordSingular;
		int newIndex = i;
		
		Map<String, Integer> twoGramMap = NGramSearch.get2GramsMap();
		Map<String, Integer> threeGramMap = ThreeGramSearch.get3GramsMap();
		
		if(i < str.length - 2){
			String thirdWord = str[i + 2];	
			String thirdWordSingular = WordForms.getSingularForm(thirdWord);
			String threeGram = twoGram + " " + thirdWord;
			String threeGramSingular = twoGram + " " + thirdWordSingular;
			String threeGramSingularSingular = twoGramSingular + " " + thirdWordSingular;				
			TokenType tokenType = TokenType.THREEGRAM;
			
			//don't want to combine three-grams with "of" in middle
			if(threeGramMap.containsKey(threeGram) && !middleWord.equals("of")){				
				if(addNGramToPairs(pairs, mathIndexList, thirdWord, threeGram, tokenType)){
					newIndex = i+2;
				}
			}else if(threeGramMap.containsKey(threeGramSingular)){				
				if(addNGramToPairs(pairs, mathIndexList, thirdWordSingular, threeGramSingular, tokenType)){
					newIndex = i+2;
				}
			}else if(threeGramMap.containsKey(threeGramSingularSingular)){				
				if(addNGramToPairs(pairs, mathIndexList, thirdWordSingular, threeGramSingularSingular, tokenType)){
					newIndex = i+2;
				}
			}	
		}
		//String twoGram = potentialTrigger;
		//Integer twoGram = twoGramMap.get(potentialTrigger);
		if(newIndex == i && i < str.length - 1){
			TokenType tokenType = TokenType.TWOGRAM;
			if(twoGramMap.containsKey(twoGram)){			
				if(addNGramToPairs(pairs, mathIndexList, nextWord, twoGram, tokenType)){
					newIndex = i+1;
				}	
			}else if(twoGramMap.containsKey(twoGramSingular)){
				if(addNGramToPairs(pairs, mathIndexList, nextWordSingular, twoGramSingular, tokenType)){
					newIndex = i+1;
				}
			}
		}
		return newIndex;
	}
	
	/**
	 * Add n gram to pairs list
	 * @param pairsList
	 * @param mathIndexList
	 * @param lastWord
	 * @param nGram
	 * @return Whether n-gram was added to pairs
	 */
	private static boolean addNGramToPairs(List<Pair> pairsList, List<Integer> mathIndexList, String lastWord,
			String nGram, TokenType tokenType) {
		//don't want 2/3 grams to end with a preposition, which can break parsing down the line
		if(posMMap.containsKey(lastWord) && posMMap.get(lastWord).get(0).equals("pre")){
			return false;
		}
		
		String pos;
		List<String> posList = posMMap.get(lastWord);		
		if(!posList.isEmpty()){
			pos = posList.get(0);
		}else{
			//try to find part of speech algorithmically
			pos = computeNGramPos(nGram, tokenType);
		}
		Pair phrasePair = new Pair(nGram, pos);								
		pairsList.add(phrasePair);
		if(pos.matches("ent")){ 
			mathIndexList.add(pairsList.size() - 1);
		}
		return true;
	}
	
	/**
	 * Attemps to compute part of speech of n-grams.
	 * @return
	 */
	private static String computeNGramPos(String nGram, TokenType tokenType){
		
		int nGramLen = nGram.length();
		String[] nGramAr = nGram.split("\\s+");
		//use enum instead!
		String pos = "";
		String firstWord = nGramAr[0];
		int firstWordLen = firstWord.length();
		
		if(tokenType.equals(TokenType.TWOGRAM)){			
			
			List<String> posList = posMMap.get(firstWord);
			boolean isFirstWordAdverb = !posList.isEmpty() ? posList.get(0).equals("adverb") : 
				(firstWord.substring(firstWordLen-2, firstWordLen).equals("ly") ? true : false);
			//adverb past-participle pair, e.g "finitely generated"
			if (isFirstWordAdverb && nGram.substring(nGramLen - 2, nGramLen).equals("ed")) {
				pos = "adj";
				
			}
		}else{
			
		}
		return pos;
	}
	
	/**
	 * Add additional extra pos to given pair.
	 * @param pair
	 * @param posList
	 */
	private static void addExtraPosToPair(Pair pair, List<String> posList){
		//don't add "noun" if "ent" already added, since these fulfill equivalent
		//roles in practice.
		
		boolean entAdded = posList.get(0).equals("ent");
		//start from 1, since first pos is already added.
		for(int k = 1; k < posList.size(); k++){
			String pos = posList.get(k);
			if(pos.equals("ent")){
				entAdded = true;
			}else if(pos.equals("noun") && entAdded){
				continue;
			}
			pair.addExtraPos(posList.get(k));
		}
	}
	
	/**
	 * Tokenizes by splitting into comma-separated strings
	 * 
	 * @param str A full sentence.
	 * @return
	 */
	/*
	 * public static void process(String sentence) throws IOException{ //can't
	 * just split! Might be in latex expression String[] subSentences =
	 * sentence.split(",|;|:"); int subSentLen = subSentences.length; for(int i
	 * = 0; i < subSentLen; i++){ parse(tokenize(subSentences[i])); }
	 * System.out.println(); }
	 */
	/**
	 * 
	 * @param sentence string to be tokenized
	 * @param parseState current state of the parse.
	 * @return List of Struct's
	 */
	public static ParseState tokenize(String sentence, ParseState parseState)  {
		
		// .....change to arraylist of Pairs, create Pair class
		// LinkedHashMap<String, String> linkedMap = new LinkedHashMap<String,
		// String>();

		// list of indices of "proper" math objects, e.g. "field", but not e.g.
		// "pair"
		List<Integer> mathIndexList = new ArrayList<Integer>();
		// list of indices of anchor words, e.g. "of"
		List<Integer> anchorList = new ArrayList<Integer>();

		// list of each word with their initial type, adj, noun,
		List<Pair> pairs = new ArrayList<Pair>();
		//boolean addIndex = true; // whether to add to pairIndex
		String[] strAr = sentence.split(" ");
		
		// int pairIndex = 0;
		strloop: for (int i = 0; i < strAr.length; i++) {

			String curWord = strAr[i];
			//why this?
			if (curWord.matches("\\s*,*")){
				continue;
			}
			Matcher negativeAdjMatcher;
			// strip away special chars '(', ')', etc ///should not
			// remove......
			// curWord = curWord.replaceAll("\\(|\\)", "");
			// remove this and ensure curWord is used subsequently
			// instead of str[i]
			strAr[i] = curWord;
			//change to enum!
			String type = "ent"; //mathObj
			int wordlen = strAr[i].length();
			
			// detect latex expressions, set their pos as "mathObj" for now
			if (curWord.charAt(0) == '$') {
				
				String latexExpr = curWord;
				int strArLength = strAr.length;
				//not a single-word latex expression, i.e. $R$-module
				if (i < strArLength - 1 && !curWord.matches("\\$[^$]+\\$[^\\s]*")
						&& (curWord.charAt(wordlen - 1) != '$' || wordlen == 2 || wordlen == 1)) {
					
					i++;
					curWord = strAr[i];
					
					if (i < strArLength - 1 && curWord.equals("")) {
						curWord = strAr[++i];
					//}
					//else if (curWord.matches("[^$]*\\$.*")) {
						//latexExpr += " " + curWord;
						//i++;
					} else {
						
						while (i < strArLength && curWord.length() > 0
								&& !curWord.matches("[^$]*\\$.*") ){//curWord.charAt(curWord.length() - 1) != '$') {
							latexExpr += " " + curWord;
							i++;

							if (i == strArLength)
								break;

							curWord = i < strArLength - 1 && strAr[i].equals("") ? strAr[++i] : strAr[i];

						}
					}
					//add the end of the latex expression, only if it's the end bit
					if (i < strArLength ) {
						int tempWordlen = strAr[i].length();

						if (tempWordlen > 0 && strAr[i].charAt(tempWordlen - 1) == '$')
							latexExpr += " " + strAr[i];
					}
					/*
					if (latexExpr.matches("[^=]+=.+|[^\\\\cong]+\\\\cong.+")
							&& (i+1 == stringLength || i+1 < stringLength && !posMap.get(str[i+1] ).matches("verb|vbs")) ) {
						type = "assert";
					} */
				} else if (curWord.matches("\\$[^$]{1,2}\\$")) {
					type = "symb";
				}
				// go with the pos of the last word, e.g. $k$-algebra
				else if (curWord.matches("\\$[^$]+\\$[^-\\s]*-[^\\s]*")) { //\\$[^$]+\\$[^-\\s]*-[^\\s]*
					
					String[] curWordAr = curWord.split("-");
					String tempWord = curWordAr[curWordAr.length - 1];
					List<String> tempPosList = posMMap.get(tempWord);
					if (!tempPosList.isEmpty()) {
						type = tempPosList.get(0);
					}
					
				}
				
				Pair pair = new Pair(latexExpr, type);
				pairs.add(pair);
				if (type.equals("ent")){
					mathIndexList.add(pairs.size() - 1);
				}				
				continue;
			}
			// check for trigger words
			else if (i < strAr.length - 1) {
				String potentialTrigger = curWord + " " + strAr[i + 1];
				if (fixedPhraseMap.containsKey(potentialTrigger)) {
					
					// need multimap!! same trigger could apply to many phrases
					// do first two words instead of 1, e.g. "for all" instead
					// of just "for"
					// since compound words contain at least 2 words
					List<FixedPhrase> fixedPhraseList = fixedPhraseMap.get(potentialTrigger);
					
					Iterator<FixedPhrase> fixedPhraseListIter = fixedPhraseList.iterator();
					while (fixedPhraseListIter.hasNext()) {
						FixedPhrase fixedPhrase = fixedPhraseListIter.next();
						int numWordsDown = fixedPhrase.numWordsDown();
						// don't really need this check
						// ... could have many pos!

						// String joined =
						// StringUtils.join(Arrays.copyOfRange(str, i,
						// str.length) );
						String joined = "";
						int k = i;
						while (k < strAr.length && k - i < numWordsDown) {
							// String joined =
							// String.join(Arrays.copyOfRange(str, i,
							// str.length) );
							joined += strAr[k] + " ";
							k++;
						}
						
						Matcher matcher = fixedPhrase.phrasePattern().matcher(joined.trim());
						if (matcher.matches()) {
							String pos = fixedPhrase.pos();
							if(!pos.equals("fluff")){
								Pair phrasePair = new Pair(joined.trim(), pos);								
								pairs.add(phrasePair);								
								if(pos.matches("ent")) mathIndexList.add(pairs.size() - 1);
							}
							
							i += numWordsDown - 1;
							
							continue strloop;
						}

					}

				}				
				int newIndex = gatherTwoThreeGram(i, strAr, pairs, mathIndexList);
				//a two or three gram was picked up
				if(newIndex > i){ 
					
					i = newIndex;
					continue;				
				}
				
			}
			
			String[] singularForms = WordForms.getSingularForms(curWord);
			
			String singular = singularForms[0];
			String singular2 = singularForms[1]; // ending in "es"
			String singular3 = singularForms[2]; // ending in "ies"
			
			boolean containsCurWord = posMMap.containsKey(curWord);
			boolean containsCurWordSingular = posMMap.containsKey(singular);
			
			String wordPos = containsCurWord ? posMMap.get(curWord).get(0) : 
				(containsCurWordSingular ? posMMap.get(singular).get(0) : null);
			
			//last condition should be temporary: should eliminate mathObjMap
			if (wordPos != null && wordPos.equals("ent") || mathObjMap.containsKey(curWord)) { 
				String tempWord = containsCurWord ? curWord : singular;;
				int pairsSize = pairs.size();
				int k = 1;
				
				//should be superceded by using the two-gram map above! <--this way can be more deliberate 
				// if composite math noun, eg "finite field"
				while (i - k > -1 && posMMap.containsKey(strAr[i - k] + " " + tempWord)
						&& posMMap.get(strAr[i - k] + " " + tempWord).get(0).equals("ent")) {
					
					// remove previous pair from pairs if it has new match
					// pairs.size should be > 0, ie previous word should be
					// classified already
					if (pairs.size() > 0 && pairs.get(pairsSize - 1).word().equals(strAr[i - k])) {

						// remove from mathIndexList if already counted
						List<String> posList = posMMap.get(pairs.get(pairs.size() - 1).word());
						if (!posList.isEmpty() && posList.get(0).equals("ent")) {
							
							mathIndexList.remove(mathIndexList.size() - 1);
						}
						pairs.remove(pairsSize - 1);
					}

					tempWord = strAr[i - k] + " " + tempWord;
					curWord = strAr[i - k] + " " + curWord;
					k++;
				}

				// if previous Pair is also an ent, fuse them
				pairsSize = pairs.size();
				if (pairs.size() > 0 && pairs.get(pairsSize - 1).pos().matches("ent")) {
					pairs.get(pairsSize - 1).set_word(pairs.get(pairsSize - 1).word() + " " + curWord);
					continue;
				}
				//System.out.println("^^^^^^^^curWord " + curWord);
				Pair pair = new Pair(curWord, "ent");
				pairs.add(pair);
				mathIndexList.add(pairs.size() - 1);

			}

			else if (anchorMap.containsKey(curWord)) {
				Pair pair = new Pair(curWord, "anchor");
				// anchorList.add(pairIndex);
				pairs.add(pair);

				int pairsSize = pairs.size();
				anchorList.add(pairsSize - 1);
			}
			// check part of speech (pos)
			else if (posMMap.containsKey(curWord)){ //|| posMMap.containsKey(curWord.toLowerCase())) {
				/*if(posMMap.containsKey(curWord.toLowerCase())){
					curWord = curWord.toLowerCase();
				}*/
				// composite words, such as "for all".
				String temp = curWord;
				String pos;
				List<String> posList = posMMap.get(temp);
				String tempPos = posList.get(0);
				//keep going until all words in an n-gram are gathered
				while (tempPos.matches("[^_]*_COMP|[^_]*_comp") && i < strAr.length - 1) {					
					curWord = temp;
					temp = temp + " " + strAr[i + 1];
					if (!posMMap.containsKey(temp)) {
						break;
					}
					tempPos = posMMap.get(temp).get(0);
					i++;
				}
				
				Pair pair;
				if (posMMap.containsKey(temp)) {
					posList = posMMap.get(temp);
					pos = posList.get(0).split("_")[0];
					pair = new Pair(temp, pos);
					//add any additional pos to pair if applicable.
					addExtraPosToPair(pair, posList);
				} else {
					//guaranteed to contain curWord at this point.
					posList = posMMap.get(curWord);
					pos = posList.get(0).split("_")[0];
					pair = new Pair(curWord, pos);
					addExtraPosToPair(pair, posList);
				}				
				pair = fuseAdverbAdj(pairs, curWord, pair);
				
				pairs.add(pair);
			}//negative adjective word, not that unusual.
			else if((negativeAdjMatcher = NEGATIVE_ADJECTIVE_PATTERN.matcher(curWord)).find()){
				
				String curAdjWord = negativeAdjMatcher.group(1);
				
				List<String> posList = posMMap.get(curAdjWord);
				
				if (!posList.isEmpty()) {
					String pos = posList.get(0).split("_")[0];
					Pair pair = new Pair(curAdjWord, pos);
					//add any additional pos to pair if applicable.
					addExtraPosToPair(pair, posList);
					pair = fuseAdverbAdj(pairs, curWord, pair);					
					pairs.add(pair);
				}
				
			}
			// if plural form
			else if (posMMap.containsKey(singular)){
				List<String> posList = posMMap.get(singular);
				//split in case type is of form "blah_comp" <--should be superceded by n-grams
				Pair pair = new Pair(singular, posList.get(0).split("_")[0]);
				pairs.add(pair);
				addExtraPosToPair(pair, posList);
			}
			else if (posMMap.containsKey(singular2)){
				List<String> posList = posMMap.get(singular2);
				Pair pair = new Pair(singular, posList.get(0).split("_")[0]);
				pairs.add(pair);				
				addExtraPosToPair(pair, posList);
			}
			else if (posMMap.containsKey(singular3)){
				List<String> posList = posMMap.get(singular3);
				Pair pair = new Pair(singular, posList.get(0).split("_")[0]);
				pairs.add(pair);	
				addExtraPosToPair(pair, posList);
			}
			// classify words with dashes; eg sesqui-linear
			// <-- these should be split during pre-processing!
			else if (curWord.split("-").length > 1) {
				String[] splitWords = curWord.split("-");
				System.out.println("splitWords: " + Arrays.toString(splitWords));
				
				String lastTerm = splitWords[splitWords.length - 1];
				String lastTermS1 = singular == null ? "" : singular.split("-")[splitWords.length - 1];
				String lastTermS2 = singular2 == null ? "" : singular2.split("-")[splitWords.length - 1];
				String lastTermS3 = singular3 == null ? "" : singular3.split("-")[splitWords.length - 1];

				String searchKey = "";
				if (posMMap.containsKey(lastTerm))
					searchKey = lastTerm;
				else if (posMMap.containsKey(lastTermS1))
					searchKey = lastTermS1;
				else if (posMMap.containsKey(lastTermS2))
					searchKey = lastTermS2;
				else if (posMMap.containsKey(lastTermS3))
					searchKey = lastTermS3;

				if (!searchKey.equals("")) {

					Pair pair = new Pair(curWord, posMMap.get(searchKey).get(0).split("_")[0]);
					pairs.add(pair);
				} // if lastTerm is entity, eg A-module
				
				
				if (isTokenEnt(lastTerm) || isTokenEnt(lastTermS1)
						|| isTokenEnt(lastTermS2) || isTokenEnt(lastTermS3)) {

					Pair pair = new Pair(curWord, "ent");
					pairs.add(pair);
					mathIndexList.add(pairs.size() - 1);
				}
			}
			// check for verbs ending in 'es' & 's'
			else if (wordlen > 0 && curWord.charAt(wordlen - 1) == 's'
					&& posMMap.containsKey(strAr[i].substring(0, wordlen - 1))
					&& posMMap.get(strAr[i].substring(0, wordlen - 1)).get(0).equals("verb")) {

				Pair pair = new Pair(strAr[i], "verb");
				pairs.add(pair);
			} else if (wordlen > 1 && curWord.charAt(wordlen - 1) == 's' && strAr[i].charAt(strAr[i].length() - 2) == 'e'
					&& posMMap.containsKey(strAr[i].substring(0, wordlen - 2))
					&& posMMap.get(strAr[i].substring(0, wordlen - 2)).get(0).equals("verb")) {
				Pair pair = new Pair(strAr[i], "verb");
				pairs.add(pair);

			}
			// adverbs that end with -ly that haven't been screened off before
			else if (wordlen > 1 && curWord.substring(wordlen - 2, wordlen).equals("ly")) {
				Pair pair = new Pair(strAr[i], "adverb");
				pairs.add(pair);
			}
			// participles and gerunds. Need special list for words such as
			// "given"
			else if (wordlen > 1 && curWord.substring(wordlen - 2, wordlen).equals("ed")
					&& (posMMap.containsKey(strAr[i].substring(0, wordlen - 2))
							&& posMMap.get(strAr[i].substring(0, wordlen - 2)).get(0).equals("verb")
							|| posMMap.containsKey(strAr[i].substring(0, wordlen - 1))
									&& posMMap.get(strAr[i].substring(0, wordlen - 1)).get(0).equals("verb"))) {

				// if next word is "by", then
				String curPos = "parti";
				int pairsSize = pairs.size();
				// if next word is "by"
				if (strAr.length > i + 1 && strAr[i + 1].equals("by")) {
					curPos = "partiby";
					curWord = curWord + " by";
					// if previous word is a verb, combine to form verb
					if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().matches("verb|vbs")) {
						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						curPos = "auxpass"; // passive auxiliary, eg "is determined by"
						pairs.remove(pairsSize - 1);
					}
					i++;
				}
				// previous word is "is, are", then group with previous word to
				// verb
				// e.g. "is called"
				else if (pairsSize > 0 && pairs.get(pairsSize - 1).word().matches("is|are|be")) {
					
					//if next word is a preposition
					if(strAr.length > i + 1 && !posMMap.get(strAr[i+1]).isEmpty() && 
							posMMap.get(strAr[i+1]).get(0).equals("pre")){
						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						pairs.remove(pairsSize - 1);
						
						curPos = "auxpass";
					}
					//otherwise likely used as adjective, e.g. "is connected"
					else {
						curPos = "adj";
					}
				}
				// if previous word is adj, "finitely presented"
				else if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().equals("adverb")) {

					curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
					pairs.remove(pairsSize - 1);

					curPos = "adj";
				}
				// if next word is entity, then adj
				else if (strAr.length > i + 1 && !posMMap.get(strAr[i+1]).isEmpty() && 
						posMMap.get(strAr[i+1]).get(0).equals("ent")) {

					// combine with adverb if previous one is adverb
					if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().equals("adverb")) {
						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						pairs.remove(pairsSize - 1);
					}
					curPos = "adj";
				}

				Pair pair = new Pair(curWord, curPos);
				pairs.add(pair);
			}//obfuscated! 
			else if (wordlen > 2 && curWord.substring(wordlen - 3, wordlen).equals("ing")
					&& (posMMap.containsKey(curWord.substring(0, wordlen - 3))
							&& posMMap.get(curWord.substring(0, wordlen - 3)).get(0).matches("verb|vbs")
							// verbs ending in "e"
							|| (posMMap.containsKey(curWord.substring(0, wordlen - 3) + 'e')
									&& posMMap.get(curWord.substring(0, wordlen - 3) + 'e').get(0).matches("verb|vbs")))) {
				String curType = "gerund";
				if (i < strAr.length - 1 && posMMap.containsKey(strAr[i + 1]) && posMMap.get(strAr[i + 1]).get(0).equals("pre")) {
					// eg "consisting of" functions as pre
					curWord = curWord + " " + strAr[++i];
					curType = "pre";
				} else if (i < strAr.length - 1 && (mathObjMap.containsKey(strAr[i+1]) ||
						posMMap.containsKey(strAr[i + 1]) && posMMap.get(strAr[i + 1]).get(0).equals("noun"))) 
				{
					// eg "vanishing locus" functions as amod: adjectivial
					// modifier
					//curWord = curWord + " " + str[++i];
					curType = "amod";
					curType = "adj"; //adj, so can be grouped together with ent's later
				}
				Pair pair = new Pair(curWord, curType);
				pairs.add(pair);
			} else if (curWord.matches("[a-zA-Z]")) {
				// variable/symbols
				Pair pair = new Pair(strAr[i], "symb");
				pairs.add(pair);
			}
			// Get numbers. Incorporate written-out numbers, eg "two"
			else if (curWord.matches("\\d+")) {
				Pair pair = new Pair(strAr[i], "num");
				pairs.add(pair);
			} else if (!curWord.matches("\\s+")) { // try to minimize this case.

				System.out.println("word not in dictionary: " + curWord);
				pairs.add(new Pair(curWord, ""));

				// write unknown words to file
				unknownWords.add(curWord);

			} else { // curWord doesn't count
				
				continue;
			}

			// if (addIndex) {
			// pairIndex++;
			// }
			//addIndex = true;

			int pairsSize = pairs.size();

			if (pairsSize > 0) {
				Pair pair = pairs.get(pairsSize - 1);

				// combine "no" and "not" with verbs
				if (pair.pos().matches("verb|vbs")) {
					if (pairs.size() > 1 && (pairs.get(pairsSize - 2).word().matches("not|no")
							|| pairs.get(pairsSize - 2).pos().matches("not"))) {
						//String newWord = pair.word().matches("is|are") ? "not" : "not " + pair.word();
						String newWord = pair.word() + " " + pairs.get(pairsSize - 2).word();
						pair.set_word(newWord);
						pairs.remove(pairsSize - 2);
					}

					if (i + 1 < strAr.length && strAr[i + 1].matches("not|no")) {
						//String newWord = pair.word().matches("is|are") ? "not" : "not " + pair.word();
						String newWord = pair.word() + " " + strAr[i+1];
						pair.set_word(newWord);
						i++;
					}
				}
			}

		}

		// If phrase isn't in dictionary, ie has type "", then use probMap to
		// postulate type, if possible
		Pair curpair;
		int pairsLen = pairs.size();

		double bestCurProb = 0;
		String prevType = "", nextType = "", tempCurType = "", tempPrevType = "", tempNextType = "", bestCurType = "";

		int posListSz = posList.size();
		
		for (int index = 0; index < pairsLen; index++) {
			//int pairsLen = pairs.size();
			curpair = pairs.get(index);
			//System.out.println("curpair " + curpair);
			if (curpair.pos().equals("")) {

				prevType = index > 0 ? pairs.get(index - 1).pos() : "";
				nextType = index < pairsLen - 1 ? pairs.get(index + 1).pos() : "";

				prevType = prevType.equals("anchor") ? "pre" : prevType;
				nextType = nextType.equals("anchor") ? "pre" : nextType;

				// iterate through list of types, ent, verb etc, to make best guess
				for (int k = 0; k < posListSz; k++) {
					tempCurType = posList.get(k);

					// FIRST/LAST indicate positions
					tempPrevType = index > 0 ? prevType + "_" + tempCurType : "FIRST";
					tempNextType = index < pairsLen - 1 ? tempCurType + "_" + nextType : "LAST";

					if (probMap.get(tempPrevType) != null && probMap.get(tempNextType) != null) {
						double score = probMap.get(tempPrevType) * probMap.get(tempNextType);
						if (score > bestCurProb) {
							bestCurProb = score;
							bestCurType = tempCurType;
						}
					}
				}
				curpair.set_pos(bestCurType);
				/*if(bestCurType.equals("ent")){ 
					System.out.println("#### ent word: " + pairs.get(index).word());
					
				}*/
				if (bestCurType.equals("ent")){
					mathIndexList.add(index);
				}
			}
		}

		// map of math entities, has mathObj + ppt's
		List<StructH<HashMap<String, String>>> mathEntList = new ArrayList<StructH<HashMap<String, String>>>();

		// second run, combine adj with math ent's
		for (int j = 0; j < mathIndexList.size(); j++) {
			
			int index = mathIndexList.get(j);
			String mathObjName = pairs.get(index).word();
			pairs.get(index).set_pos(String.valueOf(j));
			
			StructH<HashMap<String, String>> tempStructH = new StructH<HashMap<String, String>>("ent");
			List<String> posList = posMMap.get(mathObjName);
			boolean entAdded = false;
			if(!posList.isEmpty()){
				entAdded = posList.get(0).equals("ent");
			}
			//start from 1, as extraPosList only contains *additional* pos
			for(int l = 1; l < posList.size(); l++){
				String pos = posList.get(l);
				if(pos.equals("ent")){
					entAdded = true;
				}else if(pos.equals("noun") && entAdded){
					continue;
				}
				tempStructH.addExtraPos(pos); 
			}
			
			HashMap<String, String> tempMap = new HashMap<String, String>();
			tempMap.put("name", mathObjName);
			
			// if next pair is also ent, and is latex expression
			if (j < mathIndexList.size() - 1 && mathIndexList.get(j + 1) == index + 1) {
				Pair nextPair = pairs.get(index + 1);
				String name = nextPair.word();
				if (name.contains("$")) {
					tempMap.put("tex", name);
					nextPair.set_pos(String.valueOf(j));
					mathIndexList.remove(j + 1);
				}
			}

			// look right one place in pairs, if symbol found, add it to
			// namesMap
			// if it's the given name for an ent.
			// Combine gerund with ent
			int pairsSize = pairs.size();
			if (index + 1 < pairsSize && pairs.get(index + 1).pos().equals("symb")) {
				pairs.get(index + 1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index + 1).word();
				tempMap.put("called", givenName);
				// do not overwrite previously named symbol
				// if(!namesMap.containsKey(givenName))
				// namesMap.put(givenName, tempStructH);

			} /*
				 * else if ((index + 2 < pairsSize && pairs.get(index +
				 * 2).pos().equals("symb"))) { pairs.get(index +
				 * 2).set_pos(String.valueOf(j)); String givenName =
				 * pairs.get(index + 2).word(); tempMap.put("called",
				 * givenName); namesMap.put(givenName, tempStructH); }
				 */
			// look left one place
			if (index > 0 && pairs.get(index - 1).pos().equals("symb")) {
				pairs.get(index - 1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index - 1).word();
				// combine the symbol with ent's name together
				tempMap.put("name", givenName + " " + tempMap.get("name"));

			}
			// combine nouns with ent's right after, ie noun_ent
			else if (index > 0 && pairs.get(index - 1).pos().matches("noun")) {
				pairs.get(index - 1).set_pos(String.valueOf(j));
				String prevNoun = pairs.get(index - 1).word();
				tempMap.put("name", prevNoun + " " + tempMap.get("name"));
			}
			// and combine ent_noun together
			else if (index + 1 < pairsSize && pairs.get(index + 1).pos().matches("noun")) {
				pairs.get(index + 1).set_pos(String.valueOf(j));
				String prevNoun = pairs.get(index + 1).word();
				tempMap.put("name", tempMap.get("name") + " " + prevNoun);
			}

			// look to left and right
			int k = 1;
			// combine multiple adjectives into entities
			// ...get more than adj ... multi-word descriptions
			// set the pos as the current index in mathEntList
			// adjectives or determiners
				
			while (index - k > -1 && pairs.get(index - k).pos().matches("adj|det|num")) {
				Pair curPair = pairs.get(index - k);
				String curWord = curPair.word();
				String curPos = curPair.pos();
				
				//combine adverb-adj pair
				if(curPos.equals("adj") && index-k-1 > -1){
					Pair prevPair = pairs.get(index-k-1);
					if(prevPair.pos().equals("adverb")){
						curWord = prevPair.word() + " " + curWord;
						prevPair.set_pos(String.valueOf(j));
					}
				}
					
					// look for composite adj (two for now)
					/*if (index - k - 1 > -1 && !posMMap.get(curWord).isEmpty() && posMMap.get(curWord).get(0).matches("adj")) {
						// if composite adj
						if (pairs.get(index - k - 1).word().matches(adjMap.get(curWord))) {
							curWord = pairs.get(index - k - 1).word() + " " + curWord;
							// mark pos field to indicate entity
							pairs.get(index - k).set_pos(String.valueOf(j));
							k++;
						}
					}*/
	
				tempMap.put(curWord, "ppt");
				// mark the pos field in those absorbed pairs as index in
					// mathEntList
				curPair.set_pos(String.valueOf(j));
				k++;
			}
			
			// combine multiple adj connected by "and/or"
			// hacky way: check if index-k-2 is a verb, only combine adj's if
			// not
			// eg " "
			if (index - k - 2 > -1 && pairs.get(index - k).pos().matches("or|and")
					&& pairs.get(index - k - 1).pos().equals("adj")) {
				List<String> tempPosList = posMMap.get(pairs.get(index - k - 2).word());
				if (!tempPosList.isEmpty() && !tempPosList.get(0).matches("verb|vbs|verb_comp|vbs_comp")) {
					// set pos() of or/and to the right index
					pairs.get(index - k).set_pos(String.valueOf(j));
					String curWord = pairs.get(index - k - 1).word();
					tempMap.put(curWord, "ppt");
					pairs.get(index - k - 1).set_pos(String.valueOf(j));

				}
			}

			// look forwards
			k = 1;
			while (index + k < pairs.size() && pairs.get(index + k).pos().matches("adj|num")) {
				/// implement same as above

				tempMap.put(pairs.get(index + k).word(), "ppt");
				pairs.get(index + k).set_pos(String.valueOf(j));
				k++;
			}

			tempStructH.set_struct(tempMap);
			mathEntList.add(tempStructH);
		}

		// combine anchors into entities. Such as "of," "has"
		for (int j = anchorList.size() - 1; j > -1; j--) {
			int index = anchorList.get(j);
			String anchor = pairs.get(index).word();

			// combine entities, like in case of "of"
			switch (anchor) {
			case "of":
				// the expression before this anchor is an entity
				if (index > 0 && index + 1 < pairs.size()) {

					Pair nextPair = pairs.get(index + 1);
					Pair prevPair = pairs.get(index - 1);
					// should handle later with grammar rules in mx!
					// ent of ent
					if (prevPair.pos().matches("\\d+") && nextPair.pos().matches("\\d+")) {
						int mathObjIndex = Integer.valueOf(prevPair.pos());
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

						pairs.get(index).set_pos(nextPair.pos());
						Struct childStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));						
						tempStruct.add_child(childStruct, "of");
						
						// set to null instead of removing, to keep indices
						// right. If nextPair.pos != prevPair.pos().
						if (nextPair.pos() != prevPair.pos())
							mathEntList.set(Integer.valueOf(nextPair.pos()), null);

					} // "noun of ent".
					else if (prevPair.pos().matches("noun") && nextPair.pos().matches("\\d+")) {
						int mathObjIndex = Integer.valueOf(nextPair.pos());
						// Combine the something into the ent
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

						String entName = tempStruct.struct().get("name");
						tempStruct.struct().put("name", prevPair.word() + " of " + entName);

						pairs.get(index).set_pos(nextPair.pos());
						prevPair.set_pos(nextPair.pos());

					} // special case: "of form"

					// if verb_of: "consists of" -> verb
					else if (prevPair.pos().matches("verb|vbs")) {
						String prevWord = prevPair.word();
						prevPair.set_word(prevWord + " of");
						pairs.remove(index);
					} else {
						// set anchor to its normal part of speech word, like
						// "of" to pre

						pairs.get(index).set_pos(posMMap.get(anchor).get(0));
					}

				} // if the previous token is not an ent
				else {
					// set anchor to its normal part of speech word, like "of"
					// to pre
					pairs.get(index).set_pos(posMMap.get(anchor).get(0));
				}

				break;
			}

		}

		// list of structs to return
		List<Struct> structList = new ArrayList<Struct>();

		String prevPos = "-1";
		// use anchors (of, with) to gather terms together into entities
		int pairsSz = pairs.size();
		
		for (int i = 0; i < pairsSz; i++) {
			Pair curPair = pairs.get(i);
			//can pos ever be null?
			if (curPair.pos() == null)
				continue;
			//if has type "ent"
			if (curPair.pos().matches("\\d+")) {

				if (curPair.pos().equals(prevPos)) {
					continue;
				}
				StructH<HashMap<String, String>> curStruct = mathEntList.get(Integer.valueOf(curPair.pos()));
				
				//could have been set to null
				if (curStruct != null) {
					structList.add(curStruct);
				}

				prevPos = curPair.pos();

			} else {				
				//check if article
				if(curPair.pos().equals("art")){					
					if(i < pairsSz-1){
						Pair nextPair = pairs.get(i+1);
						if(nextPair.pos().matches("\\d+")){
							
							StructH<HashMap<String, String>> nextStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));						
							if (nextStruct != null) {
								nextStruct.setArticle(Article.getArticle(curPair.word()));
							}
						}
					}
					continue;
				}
				
				// current word hasn't classified into an ent, make structA
				int structListSize = structList.size();
				
				// combine adverbs into the prior verb if applicable.
				if (curPair.pos().equals("adverb")) {

					if (structListSize > 1 && structList.get(structListSize - 1).type().matches("verb|vbs")) {
						//only if the subsequent word is not an adjective, or no subsequent word
						if(i == pairsSz - 1 || !pairs.get(i+1).pos().equals("adj")){
							
							StructA<?, ?> verbStruct = (StructA<?, ?>) structList.get(structListSize - 1);
							// verbStruct should not have prev2, set prev2 type
							// to String <--improve this.
							verbStruct.set_prev2(curPair.word());
							continue;
						}
					}
				}

				String curWord = curPair.word();

				//  leaf of prev2 is empty string ""
				StructA<String, String> newStruct = 
						new StructA<String, String>(curWord, NodeType.STR, "", NodeType.STR, curPair.pos());
				//add extra pos from curPair's extraPosList. In the other case when extraPos is not added, 
				//that's when the primary pos fits well already.
				List<String> posList = curPair.extraPosList();
				if(posList != null){
					for(int l = 0; l < posList.size(); l++){
						newStruct.addExtraPos(posList.get(l));
					}
				}
				
				//combine adverb-adjective together
				if (curPair.pos().equals("adj")) {
					if (structListSize > 0 && structList.get(structListSize - 1).type().equals("adverb")) {
						
						//if(structListSize == 1 || !structList.get(structListSize - 2).type().matches("verb|vbs")){
							Struct adverbStruct = structList.get(structListSize - 1);
							String newContent = adverbStruct.prev1().toString() + " " + curWord;
							newStruct.set_prev1(newContent);
							//newStruct.set_prev2(adverbStruct);						
							// remove the adverb Struct
							structList.remove(structListSize - 1);
						//}
					}
				}
				
				/*
				 * else if (curPair.pos().equals("pre"))
				 * {////////////////////////////// if (structListSize > 0 &&
				 * structList.get(structListSize - 1).type().equals("gerund")) {
				 * Struct gerundStruct = structList.get(structListSize - 1);
				 * 
				 * newStruct.set_prev2(adverbStruct); //////////// // remove the
				 * adverb Struct structList.remove(structListSize - 1); } }
				 */

				// combine det into nouns and verbs, change
				else if (curPair.pos().equals("noun") && structListSize > 0
						&& structList.get(structListSize - 1).type().equals("det")) {
					String det = (String) structList.get(structListSize - 1).prev1();
					newStruct.set_prev2(det);
					structList.remove(structListSize - 1);
				}

				structList.add(newStruct);

			}

			// ...try multiple entities instead of first one found
			// add to list of properties
			// if adjective, group with nearest entity
			// after going through entities in sentence first
			// also templating, "is" is a big hint word
			// add as property

		}
		//System.out.println("^^^^structList: " + structList);
		
		parseState.setTokenList(structList);
		return parseState;
	}
	
	/**
	 * Determines whether the token string represents a math
	 * entity.
	 * @return
	 */
	private static boolean isTokenEnt(String word){
		List<String> lastTermPosList = posMMap.get(word);
		if(!lastTermPosList.isEmpty()){
			//iterate through list?
			if(lastTermPosList.get(0).equals("ent")){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Fuses adverb-adj pair, e.g. "fantastically awesome".
	 * @param pairs
	 * @param curWord
	 * @param pair
	 * @return
	 */
	private static Pair fuseAdverbAdj(List<Pair> pairs, String curWord, Pair pair) {
		//boolean addIndex;
		int pairsSize = pairs.size();
		
		// if adverb-adj pair, eg "clearly good"
		// And combine adj_adj to adj, eg right exact
		List<String> posList = posMMap.get(curWord);
		if (pairs.size() > 0 && !posList.isEmpty() && posList.get(0).equals("adj")) {
			if (pairs.get(pairsSize - 1).pos().matches("adverb|adj")) {
				curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
				// remove previous Pair
				pairs.remove(pairsSize - 1);
				pair = new Pair(curWord, "adj");				
				//addIndex = false;
			}			
		}
		return pair;
	}

	/**
	 * Takes in list of entities/ppt, and connectives parse using
	 * structMap, and obtain sentence structures with Chart parser.
	 * @param recentEnt, ent used to keep track of recent MathObj/Ent, for pronoun
	 * reference assignment.
	 */
	public static ParseState parse(ParseState parseState) {
		/*System.out.println("**********");
		System.out.println("Pos of open " + posMMap.get("open"));
		System.out.println("**********");*/
		//int parseContextVectorSz = TriggerMathThm2.keywordDictSize();
		//this is cumulative, should be cleared per parse! Move this back
		//to initializer after debugging!
		//parseContextVector = new int[parseContextVectorSz];
		
		List<Struct> inputStructList = parseState.getTokenList();
		Struct recentEnt = parseState.getRecentEnt();
		
		int len = inputStructList.size();
		// shouldn't be 0 to start with?!
		if (len == 0)
			return parseState;

		// first Struct
		Struct firstEnt = null;
		boolean foundFirstEnt = false;
		// track the most recent entity for use for pronouns
		//Struct recentEnt = null;
		// index for recentEnt, ensure we don't count later structs
		// for pronouns
		int recentEntIndex = -1;

		// A matrix of List's. Dimenions of first two Lists are same: square
		// matrix
		List<List<StructList>> mx = new ArrayList<List<StructList>>(len);

		for (int l = 0; l < len; l++) {
			List<StructList> tempList = new ArrayList<StructList>();

			for (int i = 0; i < len; i++) {
				// initialize Lists now so no need to repeatedly check if null
				// later
				// but does use more space! Need to revisist.
				tempList.add(new StructList());
			}

			mx.add(tempList);
			/*
			 * mx.add(new ArrayList<Struct>(len));
			 * 
			 * for (int i = 0; i < len; i++) { // add len number of null's
			 * mx.get(l).get(i) .add(null); }
			 */
		}

		// which row to start at for the next column
		int nextColStartRow = -1;
		outerloop: for (int j = 0; j < len; j++) {

			// fill in diagonal elements
			// ArrayList<Struct> diagonalStruct = new ArrayList<Struct>();

			// mx.get(j).set(j, diagonalStruct);
			Struct diagonalStruct = inputStructList.get(j);
			diagonalStruct.set_structList(mx.get(j).get(j));
			
			mx.get(j).get(j).add(diagonalStruct);
			
			String structName;
			if(diagonalStruct.isStructA()){
				//prev1 should be string, since this is a leaf struct.
				structName = diagonalStruct.prev1().toString();
			}else{
				structName = diagonalStruct.struct().get("name");
			}
			
			//create additional structs on the diagonal if extra pos present.
			List<String> extraPosList = diagonalStruct.extraPosList();
			if(extraPosList != null){
				
				for(int p = 0; p < extraPosList.size(); p++){
					String pos = extraPosList.get(p);
					Struct newStruct;
					if(pos.equals("ent")){
						StructH<HashMap<String, String>> tempStruct = new StructH<HashMap<String, String>>("ent");
						HashMap<String, String> tempMap = new HashMap<String, String>();
						tempMap.put("name", structName);
						tempStruct.set_struct(tempMap);
						newStruct = tempStruct;
					}else{
						newStruct = new StructA<String, String>(structName, NodeType.STR, "", NodeType.STR, pos);					
					}
					//System.out.println("NEW STRUCT ADDED " + newStruct);
					mx.get(j).get(j).add(newStruct);
				}
			}
			// mx.get(j).set(j, inputList.get(j));

			// startRow should actually *always* be < j
			int i = j - 1;
			if (nextColStartRow != -1 && nextColStartRow < j) {
				if (nextColStartRow == 0) {
					nextColStartRow = -1;
					continue;
				}
				i = nextColStartRow - 1;
				nextColStartRow = -1;
			}
			innerloop: for (; i >= 0; i--) {
				for (int k = j - 1; k >= i; k--) {
					// pairs are (i,k), and (k+1,j)

					StructList structList1 = mx.get(i).get(k);
					StructList structList2 = mx.get(k + 1).get(j);
					//System.out.println("structList1: " + structList1);
					// Struct struct1 = mx.get(i).get(k);
					// Struct struct2 = mx.get(k + 1).get(j);

					if (structList1 == null || structList2 == null || structList1.size() == 0
							|| structList2.size() == 0) {						
						continue;
					}

					// need to refactor to make methods more modular!

					Iterator<Struct> structList1Iter = structList1.structList().iterator();
					Iterator<Struct> structList2Iter = structList2.structList().iterator();
					
					//System.out.println("!structList1: " + structList1.structList() + " " + structList1.structList().size());
					//System.out.println("!structList2: " + structList2.structList() + " " + structList2.structList().size());
					
					while (structList1Iter.hasNext()) {

						Struct struct1 = structList1Iter.next();
						
						while (structList2Iter.hasNext()) {
							Struct struct2 = structList2Iter.next();
							
							// combine/reduce types, like or_ppt, for_ent,
							// in_ent
							String type1 = struct1.type();
							String type2 = struct2.type();
							/*if(type2.contentEquals("adj")){
								System.out.println("***struct1: " + struct1 + ". struct2: " + struct2 );
								System.out.println("isStructA? " + struct2.isStructA());
							}*/
							// for types such as conj_verbphrase
							String[] split1 = type1.split("_");
							//this causes conj_ent to be counted as ent, so should *not*
							//use "ent" type to determine whether StructH or not!
							if (split1.length > 1 && split1[0].matches("conj|disj")) {
								// if (split1.length > 1) {
								type1 = split1[1];
							}

							String[] split2 = type2.split("_");

							if (split2.length > 1 && split2[0].matches("conj|disj")) {
								// if (split2.length > 1) {

								type2 = split2[1];
							}

							// if recentEntIndex < j, it was deliberately
							// skipped in a previous pair when it was the 2nd struct.
							//Check type too, in case in future StructH can have types other than ent
							if (!struct1.isStructA() && type1.equals("ent") 
									&& (!(recentEntIndex < j) || !foundFirstEnt)) {
								if (!foundFirstEnt) {
									firstEnt = struct1;
									foundFirstEnt = true;
								}
								recentEnt = struct1;
								recentEntIndex = j;
							}

							// if pronoun, now refers to most recent ent
							// should refer to ent that's the object of previous
							// assertion,
							// sentence, or "complete" phrase
							// Note that different pronouns might need diferent
							// rules
							if (type1.equals("pro") && struct1.prev1NodeType().equals(NodeType.STR)
									&& ((String) struct1.prev1()).matches("it|they") && struct1.prev2() != null
									&& struct1.prev2().equals("")) {
								if (recentEnt != null && recentEntIndex < j) {
									String tempName = recentEnt.struct().get("name");
									// if(recentEnt.struct().get("called") !=
									// null )
									// tempName =
									// recentEnt.struct().get("called");
									String name = tempName != null ? tempName : "";
									struct1.set_prev2(name);
								}
							}

							if (type2.equals("ent") && !struct2.isStructA() && !(type1.matches("verb|pre"))) {
								if (!foundFirstEnt) {
									firstEnt = struct1;
									foundFirstEnt = true;
								}
								recentEnt = struct2;
								recentEntIndex = j;
							}

							// look up combined in struct table, like or_ent
							// get value as name for new hash table, table with
							// prev field
							// new type? entity, with extra ppt
							// name: or. combined ex: or_adj (returns ent),
							// or_ent (ent)
							String combined = type1 + "_" + type2;
							
							// handle pattern ent_of_symb
							//should *not* use "ent" type to determine whether StructH or not!
							//since conj_ent is counted as ent. Also this code is terrible.
							if (!struct1.isStructA() && type2.matches("pre") && struct2.prev1() != null
									&& struct2.prev1().toString().matches("of") && j + 1 < len
									&& inputStructList.get(j + 1).type().equals("symb")) {
								// create new child
								///
								List<Struct> childrenList = struct1.children();
								//*****
								//System.out.println("STRUCT1 " + struct1 + " CHILDREN " + struct1.children());
								
								boolean childAdded = false;
								//iterate backwards, want the latest-added child that fits
								int childrenListSize = childrenList.size();
								for(int p = childrenListSize - 1; p > -1; p--){
									Struct child = childrenList.get(p);
									if(child.type().equals("ent") && !child.isStructA()){
										child.add_child(inputStructList.get(j + 1), "of");
										inputStructList.get(j + 1).set_parentStruct(child);
										childAdded = true;
										break;
									}
								}
								if(!childAdded){
									//((StructH<?>) newStruct).add_child(struct2, childRelation);
									struct1.add_child(inputStructList.get(j + 1), "of");
									inputStructList.get(j + 1).set_parentStruct(struct1);
								}
								
								// mx.get(i).set(j + 1, struct1);
								mx.get(i).get(j + 1).add(struct1);
								//skipCol = true;
								nextColStartRow = i;
							} else if (combined.equals("pro_verb")) {
								if (struct1.prev1().equals("we") && struct2.prev1().equals("say")) {
									struct1.set_type(FLUFF);
									// mx.get(i).set(j, struct1);
									mx.get(i).get(j).add(struct1);
								}
							}

							if (combined.equals("adj_ent") && !struct2.isStructA()) {
								// update struct
								
									// should be StructH
								Struct newStruct = struct2.copy();
								String newPpt = "";
								if (struct1.type().equals("conj_adj")) {
									if (struct1.prev1() instanceof Struct) {
										newPpt += ((Struct) struct1.prev1()).prev1();
									}
									if (struct1.prev2() instanceof Struct) {
										newPpt += ((Struct) struct1.prev2()).prev1();
									}
								} else {
									
									newPpt += struct1.prev1();
								}
								newStruct.struct().put(newPpt, "ppt");
								// mx.get(i).set(j, newStruct);								
								mx.get(i).get(j).add(newStruct);
								continue innerloop;
								
							}
							//posessive pronouns with ent
							else if(combined.equals("poss_ent")){
								Struct newStruct = struct2.copy();
								//add reference to previous ent that this poss most likely refers to
								//to struct2. Put new entry in struct2.struct, with key "poss".
								//put the new field "possesivePrev" in Struct
								if(foundFirstEnt && recentEntIndex < j){
									newStruct.set_possessivePrev(recentEnt);									
								}
								
								mx.get(i).get(j).add(newStruct);
								continue innerloop;
							}
						
							// handle "is called" -- "verb_parti", also "is
							// defined"
							// for definitions
							else if (combined.matches("verb_parti") && struct1.prev1().toString().matches("is|are|be")
									&& struct2.prev1().toString().matches("called|defined|said|denoted")) {
								String called = "";
								int l = j + 1;
								// whether definition has started, ie "is called
								// subgroup of G"
								boolean defStarted = false;
								while (l < len) {

									Struct nextStruct = inputStructList.get(l);
									if (!nextStruct.type().matches("pre|prep|be|verb")) {
										defStarted = true;

										if (nextStruct instanceof StructA) {
											called += nextStruct.prev1();
										} else {
											called += nextStruct.struct().get("name");
										}

										if (l != len - 1)
											called += " ";

									}
									// reached end of newly defined word, now
									// more usual sentence
									// ie move from "subgroup" to "of G"
									else if (defStarted) {
										// remove last added space
										called = called.trim();
										break;
									}
									l++;
								}

								// ******* be careful using first ent
								// record the symbol/given name associated to an
								// ent, in case referring to it later. Should use custom map
								//instead of mathObjMap
								if (firstEnt != null) {
									StructA<Struct, String> parentStruct = 
											new StructA<Struct, String>(firstEnt, NodeType.STRUCTH, 
													called, NodeType.STR, "def", mx.get(0).get(len - 1));
									firstEnt.set_parentStruct(parentStruct);
									
									// mx.get(0).set(len - 1, parentStruct);
									mx.get(0).get(len - 1).add(parentStruct);

									// add to mathObj map, <--should not modify during runtime! Should add to 
									//custom map
									int q = 0;
									String[] calledArray = called.split(" ");
									String curWord = "";

									while (q < calledArray.length - 1) {
										curWord += calledArray[q];
										// if(q != calledArray.length - 1)
										curWord += " ";

										mathObjMap.put(curWord, "COMP");
										q++;
									}
									curWord += calledArray[calledArray.length - 1];
									mathObjMap.put(curWord, "ent");

									// recentEnt is defined to be "called"
									variableNamesMap.put(called, recentEnt);
									continue outerloop;
								}
							}

							// search for tokens larger than immediate ones
							// in case A_or_A or A_and_A set the mx element
							// right below
							// to null
							// to set precedence, so A won't be grouped to
							// others later
							// and if the next word is a verb, it is not
							// singular
							// ie F and G is isomorphic

							//////// %%%%%%%%%%% NPE for "subset F of G and
							//////// subset G of H"
							// because G gets null'ed out after "of". Need
							//////// better strategy!
							
							// iterate through the List at position (i-1, i-1)
							if (i > 0 && i + 1 < len) {

								/*
								 * List<Struct> iMinusOneStructList = mx.get(i -
								 * 1).get(i - 1).structList();
								 * 
								 * if (type1.matches("or|and") &&
								 * iMinusOneStructList.size() > 0) {
								 * 
								 * // Iterator<Struct> iMinusOnestructIter = //
								 * mx.get(i-1).get(i-1).iterator();
								 * 
								 * for (Struct iMinusOneStruct :
								 * iMinusOneStructList) {
								 * 
								 * if(type1.matches("and"))
								 * System.out.print("debug"); if
								 * (type2.equals(iMinusOneStruct.type())) {
								 *							
								 * 
								 * // set parent struct in row // above //
								 * mx.get(i - 1).set(j, // parentStruct);
								 * mx.get(i - 1).get(j).add(parentStruct); //
								 * set the next token to "", so // not //
								 * classified again // with others //
								 * mx.get(i+1).set(j, null); // already
								 * classified, no need // to // keep reduce with
								 * mx // manipulations break; } } } // this case
								 * can be combined with if // statement //
								 * above, use single while loop } else
								 */
								if (type1.matches("or|and")) {
									int l = 1;
									//boolean stopLoop = false;

									searchConjLoop: while (i - l > -1 && i + 1 < len) {

										List<Struct> structArrayList = mx.get(i - l).get(i - 1).structList();

										int structArrayListSz = structArrayList.size();
										if (structArrayListSz == 0) {
											l++;
											continue;
										}
										// iterate over Structs at (i-l, i-1)
										for (int p = 0; p < structArrayListSz; p++) {
											Struct p_struct = structArrayList.get(p);
											if (type2.equals(p_struct.type())) {

												// In case of conj, only proceed
												// if // next
												// word is not a singular verb.
												// // Single case with
												// complicated
												// logic, // so it's easier more
												// readable to // write //
												// if(this
												// case){ // }then{do_something}
												// // over if(not
												// this // case){do_something}
												Struct nextStruct = j + 1 < len ? inputStructList.get(j + 1) : null;
												if (nextStruct != null && type1.equals("and")
														&& nextStruct.prev1() instanceof String
														&& isSingularVerb((String) nextStruct.prev1())) {

												} else {

													String newType = type1.matches("or") ? "disj" : "conj";
													// type is expression, eg "a
													// and
													// b".
													// Come up with a scoring
													// system
													// for and/or!
													// should work a score in to
													// conj/disj! The longer the
													// conj/disj the higher
													NodeType struct1Type = struct1.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
													NodeType struct2Type = struct2.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
													
													StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(
															p_struct, struct1Type, struct2, struct2Type, newType + "_" + type2,
															mx.get(i - l).get(j));
													p_struct.set_parentStruct(parentStruct);
													struct2.set_parentStruct(parentStruct);
													//types are same, so scores should be same, so should only be punished once
													//use score instead of product
													double maxDownPathScore = p_struct.maxDownPathScore();
													parentStruct.set_maxDownPathScore(maxDownPathScore);

													mx.get(i - l).get(j).add(parentStruct);
													// mx.get(i - l).set(j,
													// parentStruct);
													// mx.get(i+1).set(j, null);
													//stopLoop = true;
													break searchConjLoop;
												}
											}
										}
										// if (stopLoop)
										// break;
										l++;
									}

								}
							}

							// potentially change assert to latex expr
							if (type2.equals("assert") && struct2.prev1NodeType().equals(NodeType.STR)
									&& ((String) struct2.prev1()).charAt(0) == '$'
									&& !structMap.containsKey(combined)) {
								struct2.set_type("expr");
								combined = type1 + "_" + "expr";
							}
							// update namesMap
							if (type1.equals("ent") && !struct1.isStructA()) {
								String called = struct1.struct().get("called");
								if (called != null)
									variableNamesMap.put(called, struct1);
							}

							// reduce if structMap contains combined
							if (structMap.containsKey(combined)) {
								Collection<Rule> ruleCol = structMap.get(combined);

								Iterator<Rule> ruleColIter = ruleCol.iterator();
								while (ruleColIter.hasNext()) {
									Rule ruleColNext = ruleColIter.next();
									reduce(mx, ruleColNext, struct1, struct2, firstEnt, recentEnt, recentEntIndex, i, j,
											k, type1, type2, parseState);
								}
							}

						} // loop listIter1 ends here
					} // loop listIter2 ends here

					// loop for (int k = j - 1; k >= i; k--) { ends here
				}

			}
			// if (skipCol)
			// j++;
		}

		// string together the parsed pieces
		// ArrayList (better at get/set) or LinkedList (better at add/remove)?
		// iterating over all headStruct
		StructList headStructList = mx.get(0).get(len - 1);
		int headStructListSz = headStructList.size();
		System.out.println("headStructListSz " + headStructListSz);
		//the default entry value in context vec should be the average of all entries in matrix.
		//list of context vectors.
		List<int[]> contextVecList = new ArrayList<int[]>();		
		
		if (headStructListSz > 0) {		
			StringBuilder parsedSB = new StringBuilder();
			
			// System.out.println("index of highest score: " +
			// ArrayDFS(headStructList));
			//temporary list to store the ParsedPairs to be sorted
			List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
			//list of ParsedPairs used to store long forms, same order of pairs as in parsedPairMMapList
			List<ParsedPair> longFormParsedPairList = new ArrayList<ParsedPair>();
			for (int u = 0; u < headStructListSz; u++) {
				Struct uHeadStruct = headStructList.structList().get(u);
				uHeadStruct.set_dfsDepth(0);
				
				int[] curStructContextvec = new int[parseContextVectorSz];		
				//keep track of span of longForm, used as commandNumUnits in case
				//no WLCommand parse. The bigger the better.
				int span = 0;
				//get the "long" form, not WL form, with this dfs
				span = dfs(uHeadStruct, parsedSB, span);
				//now get the WL form build from WLCommand's
				//should pass in the wlSB, instead of creating one anew each time. <--It's ok.
				StringBuilder wlSB = treeTraversal(uHeadStruct, parsedPairMMapList, curStructContextvec, span);
				contextVecList.add(curStructContextvec);
				
				System.out.println("+++Previous long parse: " + parsedSB);
				
				double maxDownPathScore = uHeadStruct.maxDownPathScore();
				//defer these additions to orderPairsAndPutToLists()
				//parsedExpr.add(new ParsedPair(wlSB.toString(), maxDownPathScore, "short"));		
				//System.out.println("*******SHORT FORM: " + wlSB);
				//parsedExpr.add(new ParsedPair(parsedSB.toString(), maxDownPathScore, "long"));				
				
				longFormParsedPairList.add(new ParsedPair(parsedSB.toString(), maxDownPathScore, "long"));
				
				System.out.println(maxDownPathScore);
				System.out.println(uHeadStruct.numUnits());

				String parsedString = ParseToWL.parseToWL(uHeadStruct);
				//parsedExpr.add(new ParsedPair(parsedString, maxDownPathScore, "wl"));
				System.out.print(parsedString + " \n ** ");
				
				parsedSB.setLength(0); //should just declare new StringBuilder instead!
			}
			//order maps from parsedPairMMapList and put into parseStructMapList and parsedExpr.
			//Also add context vector of highest scores
			
			orderPairsAndPutToLists(parsedPairMMapList, longFormParsedPairList, contextVecList);
			//parseStructMapList.add(parseStructMap.toString() + "\n");
		}
		// if no full parse. Also add to parsedExpr List.
		else {
			List<StructList> parsedStructList = new ArrayList<StructList>();

			int i = 0, j = len - 1;
			while (j > -1) {
				i = 0;
				while (mx.get(i).get(j).size() == 0) {
					i++;
					// some diagonal elements can be set to null on purpose
					if (i >= j) {
						break;
					}
				}

				StructList tempStructList = mx.get(i).get(j);

				// if not null or fluff.
				// What kind of fluff can trickle down here??
				// for the check !tempStruct.type().equals(FLUFF)
				if (tempStructList.size() > 0) {
					parsedStructList.add(0, tempStructList);
				}
				// a singleton on the diagonal <--not necessarily true any more
				if (i == j) {
					j--;
				} else {
					j = i - 1;
				}
			}

			// if not full parse, try to make into full parse by fishing out the
			// essential sentence structure, and discarding the phrases still
			// not
			// labeled after 2nd round
			// parse2(parsedStructList);

			// print out the components
			int parsedStructListSize = parsedStructList.size();
			//String totalParsedString = "";
			double totalScore = 1; //product of component scores
			List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
			//list of long forms, with flattened tree structure.
			List<ParsedPair> longFormParsedPairList = new ArrayList<ParsedPair>();
			
			int[] curStructContextvec = new int[TriggerMathThm2.keywordDictSize()];	
			
			for (int k = 0; k < parsedStructListSize; k++) {
				StringBuilder parsedSB = new StringBuilder();
				// int highestScoreIndex = ArrayDFS(parsedStructList.get(k));
				int highestScoreIndex = 0;
				Struct kHeadStruct = parsedStructList.get(k).structList().get(highestScoreIndex);
				//measures span of longForm (how many leaf nodes reached), use in place of 
				//commandNumUnits if no full WLCommand parse.
				int span = 0;
				kHeadStruct.set_dfsDepth(0);
				span = dfs(kHeadStruct, parsedSB, span);
				//only getting first component parse. Should use priority queue instead of list?
				//or at least get highest score
				totalScore *= kHeadStruct.maxDownPathScore();
				String parsedString = ParseToWL.parseToWL(kHeadStruct);
				
				if (k < parsedStructListSize - 1){
					//totalParsedString += parsedString + "; ";
					System.out.print(";  ");
					parsedSB.append("; ");
				}
				
				StringBuilder wlSB = treeTraversal(kHeadStruct, parsedPairMMapList, curStructContextvec, span);
				longFormParsedPairList.add(new ParsedPair(parsedSB.toString(), totalScore, "long"));
			}
			
			//add only one vector to list since the pieces are part of one single parse, if no full parse
			contextVecList.add(curStructContextvec);
			
			//defer these to ordered addition in orderPairsAndPutToLists!
			//parsedExpr.add(new ParsedPair(parsedSB.toString(), totalScore, "long"));
			
			//System.out.println(totalParsedString + "; ");
			//parsedExpr.add(new ParsedPair(totalParsedString, totalScore, "wl"));
			
			orderPairsAndPutToLists(parsedPairMMapList, longFormParsedPairList, contextVecList);
			
			System.out.println("%%%%%\n");
		}
		// print out scores
		/*
		 * StructList headStructList = mx.get(0).get(len-1); int
		 * headStructListSz = headStructList.size(); System.out.println(
		 * "headStructListSz " + headStructListSz ); for(int u = 0; u <
		 * headStructListSz; u++){
		 * System.out.println(headStructList.structList().get(u) ); }
		 */

		/*
		 * Don't delete this part! System.out.println("\nWL: "); StructList
		 * headStructList = mx.get(len - 1).get(len - 1); //should pick out best
		 * parse before WL and just parse to WL for that particular parse!
		 */
		parseState.setRecentEnt(recentEnt);
		return parseState;
	}

	/**
	 * Order parsedPairMMapList and add to parseStructMapList and parsedExpr (both static members).
	 * @param parsedPairMMapList
	 * @param longFormParsedPairList List of long forms.
	 * @param contextVecList is list of context vectors, pick out the highest one and use as global context vec.
	 * contextVecList does not need to have same size as parsedPairMMapList or longFormParsedPairList, which have
	 * the same length. Length 1 if no spanning parse.
	 */
	private static void orderPairsAndPutToLists(List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList,
			List<ParsedPair> longFormParsedPairList, List<int[]> contextVecList){
		
		//use insertion sort, since list of maps is usually very small, ~1-5
		//for maps with multiple entries (e.g. one sentence with both a HYP and a STM), add the numUnits and 
		//commandNumUnits across entries.
		List<Multimap<ParseStructType, ParsedPair>> sortedParsedPairMMapList = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
		//evolving list of numUnits scores for elements in sortedParsedPairMMapList
		List<Integer> numUnitsList = new ArrayList<Integer>();
		List<Integer> commandNumUnitsList = new ArrayList<Integer>();
		//use scores to tie-break once scores become more comprehensive
		//List<Double> scoresList = new ArrayList<Double>();
		
		//ordering in the sorted list, the value list.get(i) is the index of the pair in the original parsedPairMMapList
		List<Integer> finalOrderingList = new ArrayList<Integer>();
		//this shouldn't be necessary, since can use .add to finalOrderingList works instead of .set
		for(int i = 0; i < parsedPairMMapList.size(); i++){
			finalOrderingList.add(0);
		}
		Multimap<ParseStructType, ParsedPair> firstMMap = parsedPairMMapList.get(0);
		sortedParsedPairMMapList.add(firstMMap);
		
		int firstNumUnits = 0;
		int firstCommandNumUnits = 0;
		for(ParsedPair parsedPair : firstMMap.values()){
			firstNumUnits += parsedPair.numUnits;
			firstCommandNumUnits += parsedPair.commandNumUnits;
		}
		System.out.println("****parsedPairMMapList " + parsedPairMMapList);
		numUnitsList.add(firstNumUnits);
		commandNumUnitsList.add(firstCommandNumUnits);
		//finalOrderingList.add(0, 0);
		
		for(int i = 1; i < parsedPairMMapList.size(); i++){
			int numUnits = 0;
			int commandNumUnits = 0;
			Multimap<ParseStructType, ParsedPair> mmap = parsedPairMMapList.get(i);
			for(ParsedPair parsedPair : mmap.values()){
				numUnits += parsedPair.numUnits;
				commandNumUnits += parsedPair.commandNumUnits;
			}
			
			int listSz = sortedParsedPairMMapList.size();
			//put into sortedParsedPairMMapList in sorted order, best parse first
			//should sort rest according to numUnits!
			for(int j = 0; j < listSz; j++){
				//Multimap<ParseStructType, ParsedPair> sortedMap = sortedParsedPairMMapList.get(j);
				//commandNumUnits weigh the most, use numUnits as tie-breakers, use numUnits if
				//commandNumUnits differ by more than 1. Count commandNumUnits diff 3/2 as much weight
				//as numUnits diff
				//simplify this to use only one if!
				int sortedNumUnits = numUnitsList.get(j);
				int sortedCommandNumUnits = commandNumUnitsList.get(j);
				//double commandNumUnitsDiff = ((double)sortedCommandNumUnits - commandNumUnits)*3/2;				
				if(sortedCommandNumUnits < commandNumUnits 						
						|| (sortedNumUnits - numUnits) > ((double)sortedCommandNumUnits - commandNumUnits)*3/2){
					//insert
					sortedParsedPairMMapList.add(j, mmap);
					numUnitsList.add(j, numUnits);
					commandNumUnitsList.add(j, commandNumUnits);
					finalOrderingList.add(j, i);
					break;
				}//but this case was already included above
				else if(sortedCommandNumUnits == commandNumUnits && sortedNumUnits > numUnits){
					//sort based on numUnits if commandNumUnits are the same 
					sortedParsedPairMMapList.add(j, mmap);
					numUnitsList.add(j, numUnits);
					commandNumUnitsList.add(j, commandNumUnits);
					finalOrderingList.add(j, i);
					break;
				}
			}
			if(listSz == sortedParsedPairMMapList.size()){
				sortedParsedPairMMapList.add(listSz, mmap);
				numUnitsList.add(listSz, numUnits);
				commandNumUnitsList.add(listSz, commandNumUnits);
				finalOrderingList.add(listSz, i);
			}
			
		}
		System.out.println("##commandNumUnitsList " + commandNumUnitsList );
		for(int i = 0; i < sortedParsedPairMMapList.size(); i++){
			Multimap<ParseStructType, ParsedPair> map = sortedParsedPairMMapList.get(i);
			
			parseStructMapList.add(map.toString() + "\n");
			parseStructMaps.add(map);
			
			//add to parsedExpr  parsedExpr.add(new ParsedPair(totalParsedString, totalScore, "wl"));
			//note that Multimap does not necessarily preserve insertion order!
			for(Map.Entry<ParseStructType, ParsedPair> structTypePair : map.entries()){
				ParsedPair pair = structTypePair.getValue();
				ParseStructType parseStructType = structTypePair.getKey();
				parsedExpr.add(new ParsedPair(pair.parsedStr, pair.score, pair.numUnits, pair.commandNumUnits, parseStructType));				
			}
			//add the long form to parsedExpr
			parsedExpr.add(longFormParsedPairList.get(finalOrderingList.get(i)));
			System.out.println("longForm, commandUnits: " + commandNumUnitsList.get(i) +". numUnits: " +numUnitsList.get(i) + " "+ longFormParsedPairList.get(finalOrderingList.get(i)));
		}	
		//assign the global context vec as the vec of the highest-ranked parse
		if(contextVecList.size() == 1){
			//in case there was no full parse, list should only contain one element.
			//since same vector was passed around to be filled.
			parseContextVector = contextVecList.get(0);
		}else{
			parseContextVector = contextVecList.get(finalOrderingList.get(0));
			System.out.println("Best context vector added: " +  Arrays.toString(parseContextVector));
		}
		
	}
	
	/**
	 * Traverses and produces parse tree by calling various dfs methods,
	 * by matching commands. Returns
	 * string representation of the parse tree. Tree uses WLCommands.
	 * @param uHeadStruct
	 * @return LongForm parse
	 */
	private static StringBuilder treeTraversal(Struct uHeadStruct, List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList,
			int[] curStructContextVec, int span) {
		
		StringBuilder parseStructSB = new StringBuilder();
		ParseStructType parseStructType = ParseStructType.getType(uHeadStruct);
		ParseStruct headParseStruct = new ParseStruct(parseStructType, "", uHeadStruct);
		//whether to print the commands in tiers with the spaces in subsequent lines.
		boolean printTiers = false;
		//builds the parse tree by matching triggered commands. 
		ParseToWLTree.dfs(uHeadStruct, parseStructSB, headParseStruct, 0, printTiers);
		System.out.println("\n DONE ParseStruct DFS  + parseStructSB:" + parseStructSB + "  \n");
		StringBuilder wlSB = new StringBuilder();
		/**
		 * Map of parts used to build up a theorem/def etc. 
		 * Parts can be any ParseStructType. Should make this a local var.
		 */
		Multimap<ParseStructType, ParsedPair> parseStructMap = ArrayListMultimap.create();
		//initialize context vector for this command <--need to expose by adding to list
		//length is total number of terms in corpus, i.e. row dimension in term-document matrix in search
		//int[] curStructContextVec = new int[TriggerMathThm2.keywordDictSize()];
		
		//fills the parseStructMap and produces String representation		
		boolean contextVecConstructed = false;
		contextVecConstructed = ParseToWLTree.dfs(parseStructMap, uHeadStruct, wlSB, curStructContextVec, true,
				contextVecConstructed);
		if(!contextVecConstructed){
			ParseTreeToVec.tree2vec(uHeadStruct, curStructContextVec);
		}
		//System.out.println("curStructContextVec " + curStructContextVec);		
		
		System.out.println("Parts: " + parseStructMap);
		//**parseStructMapList.add(parseStructMap.toString() + "\n");
		//if parseStructMap empty, ie no WLCommand was found, but long parse form might still be good
		if(parseStructMap.isEmpty()){
			ParsedPair pair = new ParsedPair(wlSB.toString(), uHeadStruct.maxDownPathScore(), 
					uHeadStruct.numUnits(), span);
			//partsMap.put(type, curWrapper.WLCommandStr);	
			parseStructMap.put(ParseStructType.NONE, pair);
		}
		
		parsedPairMMapList.add(parseStructMap);
		
		//ParseToWLTree.dfs(uHeadStruct, wlSB, true);		
		//parsedSB.append("\n");
		//ParseToWLTree.dfs(uHeadStruct, parsedSB, true);	
		System.out.println(wlSB);
		ParseToWLTree.dfsCleanUp(uHeadStruct);
		System.out.println("~~~ DONE WLCommands DFS ~~~");
		return wlSB;
	}

	/**
	 * Returns true iff word is a singular verb (meaning associated to singular
	 * subj, eg "gives")
	 * 
	 * @param word
	 *            word to be checked
	 * @return
	 */
	private static boolean isSingularVerb(String word) {
		// did not find singular verbs that don't end in "s"
		int wordLen = word.length();

		if (wordLen < 2 || word.charAt(wordLen - 1) != 's')
			return false;

		// strip away 's'
		List<String> posList = posMMap.get(word.substring(0, wordLen - 2));
		//String pos = posMMap.get(word.substring(0, wordLen - 2)).get(0);
		if (!posList.isEmpty() && posList.get(0).matches("verb|verb_comp")) {
			return true;
		}
		// strip away es if applicable
		else if (wordLen > 2 && word.charAt(wordLen - 2) == 'e') {
			posList = posMMap.get(word.substring(0, wordLen - 3));


			if (!posList.isEmpty() && posList.get(0).matches("verb|verb_comp"))
				return true;
		}
		// could be special singular form, eg "is"
		else if (!(posList = posMMap.get(word)).isEmpty() && posList.get(0).matches("vbs|vbs_comp")) {
			return true;
		}
		return false;
	}

	/**
	 * Reduce based on returned grammar rules.
	 * @param mx
	 * @param newRule
	 * @param struct1
	 * @param struct2
	 * @param firstEnt
	 * @param recentEnt
	 * @param recentEntIndex
	 * @param i
	 * @param j
	 * @param k
	 * @param type1
	 * @param type2
	 * @param parseState
	 */
	public static void reduce(List<List<StructList>> mx, Rule newRule, Struct struct1, Struct struct2,
			Struct firstEnt, Struct recentEnt, int recentEntIndex, int i, int j, int k, String type1, String type2,
			ParseState parseState) {
		
		/*if(type2.equals("verbphrase")){
			System.out.println("Reducing _verbphrase! i " + newRule.relation() + ". struct2.pre2: " + struct2.prev2());
		}*/
		String newType = newRule.relation();
		double newScore = newRule.prob();
		double newDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
		double parentDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
		int parentNumUnits = struct1.numUnits() + struct2.numUnits();
		
		// newChild means to fuse second entity into first one
		if (newType.equals("newchild")) {
			// struct1 should be of type StructH to receive a
			// child
			// assert ensures rule correctness
			assert struct1 instanceof StructH;

			// get a (semi)deep copy of this StructH, since
			// later-added children may not
			// get used eventually, ie hard to remove children
			// added during mx building
			// that are not picked up by the eventual parse
			Struct newStruct = struct1.copy();

			// update firstEnt so firstEnt has the right children
			if (firstEnt == struct1) {
				firstEnt = newStruct;
			}

			// add to child relation, usually a preposition, eg
			// "from", "over"
			// could also be verb, "consist", "lies"			
			List<Struct> kPlus1StructArrayList = mx.get(k + 1).get(k + 1).structList();
			String childRelation = null;
			for(int p = 0; p < kPlus1StructArrayList.size(); p++){
				Struct struct = kPlus1StructArrayList.get(p);
				if(struct.prev1NodeType().equals(NodeType.STR)){
					childRelation = struct.prev1().toString();
					break;
				}
			}
			if(childRelation == null){
				//should not be null!
				System.out.println("Inside ThmP1.reduce(), childRelation should not be null!");
				return;
			}
			
			// String childRelation = mx.get(k + 1).get(k +
			// 1).prev1().toString();
			//should not even call add child if struct1 is a StructA
			if (!struct1.isStructA()) {
				// why does this cast not trigger unchecked warning <-- Because wildcard.
				//if already has child that's pre_ent, attach to that child,
				//e.g. A with B over C. <--This led to bug with too many children added!
				/*List<Struct> childrenList = newStruct.children();
				boolean childAdded = false;
				//iterate backwards, want the latest-added child that fits
				int childrenListSize = childrenList.size();
				for(int p = childrenListSize - 1; p > -1; p--){
					Struct child = childrenList.get(p);
					if(child.type().equals("ent") && !child.isStructA()){
						child.add_child(struct2, childRelation);
						struct2.set_parentStruct(child);
						childAdded = true;
						break;
					}
				}*/
				 
					//if struct2 is eg a prep, only want the ent in the prep
					//to be added. Try to avoid the "instanceof" and cast.
				Struct childToAdd = struct2;
				if(struct2.type().equals("prep") && struct2.prev2() instanceof StructH){
					childToAdd = (Struct)struct2.prev2();
				}
				((StructH<?>) newStruct).add_child(childToAdd, childRelation); 
				struct2.set_parentStruct(newStruct);
				
				recentEnt = newStruct;
				recentEntIndex = j;
			}

			newStruct.set_maxDownPathScore(newDownPathScore);

			// mx.get(i).set(j, newStruct);
			mx.get(i).get(j).add(newStruct);

		} 
		else if (newType.equals("addstruct")){
			// add struct2 content to struct1.struct, depending on type2
			if(type2.equals("expr")){
				Struct newStruct = struct1.copy();

				// update firstEnt so to have the right children
				if (firstEnt == struct1) {
					firstEnt = newStruct;
				}

				if (struct1 instanceof StructH && struct2.prev1() instanceof String) {
					((StructH<?>) newStruct).struct().put("tex", (String)struct2.prev1());
				}
				
				recentEnt = newStruct;
				recentEntIndex = j;
				
				newStruct.set_maxDownPathScore(newDownPathScore);

				mx.get(i).get(j).add(newStruct);
			}
		}else if(newType.equals("absorb")){
			//absorb struct1 into struct2
			if(struct1.isStructA() && !struct2.isStructA()){
				Struct newStruct = struct2.copy();				
				//add as property
				String ppt = struct1.prev1().toString();
				newStruct.struct().put(ppt, "ppt");
				// update firstEnt so to have the right children
				if (firstEnt == struct2) {
					firstEnt = newStruct;
				}
				recentEnt = newStruct;
				recentEntIndex = j;
				newStruct.set_maxDownPathScore(newDownPathScore);
				mx.get(i).get(j).add(newStruct);
			}
		}
		//"if A is p so is B", make substitution with recent Ent
		else if(newType.equals("So")){
			Struct recentAssert = parseState.getRecentAssert();
			
			if(recentAssert != null){
				Struct parentPrev1;
				Struct parentPrev2;
				//"so has B", "so does B", etc
				if(recentAssert.prev2NodeType().isTypeStruct() && struct2.prev2NodeType().isTypeStruct()){
					parentPrev1 = (Struct) struct2.prev2();
					parentPrev2 = (Struct) recentAssert.prev2();					
					
					NodeType struct1Type = parentPrev1.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
					NodeType struct2Type = parentPrev2.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
					
					String updatedType = "assert";
					StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(parentPrev1, struct1Type, 
							parentPrev2, struct2Type, updatedType, newScore, mx.get(i).get(j), parentDownPathScore, parentNumUnits);
					
					parentPrev1.set_parentStruct(parentStruct);
					//struct2.set_parentStruct(parentStruct);
					
					// mx.get(i).set(j, parentStruct);
					mx.get(i).get(j).add(parentStruct);					
				}				
			}
		}
		else if (newType.equals("noun")) {
			if (type1.matches("adj") && type2.matches("noun")) {
				// combine adj and noun
				String adj = (String) struct1.prev1();
				struct2.set_prev1(adj + " " + struct2.prev1());
				struct2.set_maxDownPathScore(struct2.maxDownPathScore() * newScore);
				// mx.get(i).set(j, struct2);
				mx.get(i).get(j).add(struct2);
			}
		} 
		else {
			// if symbol and a given name to some entity
			// use "called" to relate entities
			if (type1.equals("symb") && struct1.prev1NodeType().equals(NodeType.STR)) {
				String entKey = struct1.prev1().toString();
				List<Struct> namesList = variableNamesMap.get(entKey);
				if (!namesList.isEmpty()) {
					int namesListLen = namesList.size();
					Struct curEnt = namesList.get(namesListLen-1);
					struct1.set_prev2(curEnt.struct().get("name"));					
				}
			}

			// update struct2 with name if applicable
			// type could have been stripped down from conj_symb
			if (type2.equals("symb") && struct2.prev1NodeType().equals(NodeType.STR)) {

				String entKey = struct2.prev1().toString();
				List<Struct> namesList = variableNamesMap.get(entKey);
				if (!namesList.isEmpty()) {
					int namesListLen = namesList.size();
					Struct curEnt = namesList.get(namesListLen-1);
					struct2.set_prev2(curEnt.struct().get("name"));
				}
			} 			
			else if(newType.equals("assert_prep")){
				//make 2nd type into "hypo"
				struct2.set_type("hypo");
			}
			// add to namesMap if letbe defines a name for an ent
			else if (newType.equals("letbe") && mx.get(i + 1).get(k).size() > 0 && mx.get(k + 2).get(j).size() > 0) {
				// temporary patch Rewrite StructA to avoid cast
				// assert(struct1 instanceof StructA);
				// assert(struct2 instanceof StructA);
				// get previous nodes

				// now need to iterate through structList's for these two
				// Structs
				List<Struct> tempSymStructList = mx.get(i + 1).get(k).structList();
				List<Struct> tempEntStructList = mx.get(k + 2).get(j).structList();

				ploop: for (int p = 0; p < tempSymStructList.size(); p++) {
					Struct tempSymStruct = tempSymStructList.get(p);

					if (tempSymStruct.type().equals("symb")) {

						for (int q = 0; q < tempSymStructList.size(); q++) {

							Struct tempEntStruct = tempEntStructList.get(q);
							if (!tempEntStruct.isStructA() && tempEntStruct.type().equals("ent")) {

								// assumes that symb is a leaf struct! Need to
								// make more robust
								variableNamesMap.put(tempSymStruct.prev1().toString(), tempEntStruct);

								tempEntStruct.struct().put("called", tempSymStruct.prev1().toString());
								break ploop;
							}
						}
					}
				}
			}
			
			// create new StructA and put in mx, along with score for
			// struct1_struct2 combo
			//double parentDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
			//int parentNumUnits = struct1.numUnits() + struct2.numUnits();
			
			NodeType struct1Type = struct1.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
			NodeType struct2Type = struct2.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
			
			StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(struct1, struct1Type, 
					struct2, struct2Type, newType, newScore, mx.get(i).get(j), parentDownPathScore, parentNumUnits);
			struct1.set_parentStruct(parentStruct);
			struct2.set_parentStruct(parentStruct);

			if(newType.equals("assert")){
				parseState.setRecentAssert(parentStruct);
				//System.out.println("Assert added! parentStruct" + parentStruct);
			}
			
			// mx.get(i).set(j, parentStruct);
			mx.get(i).get(j).add(parentStruct);
		}
		// found a grammar rule match, move on to next mx column
		// *****actually, should keep going and keep scores!
		// break;

	}

	/**
	 * Entry point for depth first first. Initialize score mx of List's of
	 * MatrixPathNodes
	 * 
	 * @param structList,
	 *            the head, at mx position (len-1, len-1)
	 * @return
	 */
	public static int ArrayDFS(StructList structList) {
		// ArrayList<MatrixPathNode> mxPathNodeList = new
		// ArrayList<MatrixPathNode>();
		// fill in the list of MatrixPathNode's from structList

		// highest score encountered so far in list
		double highestDownScore = 0;
		int highestDownScoreIndex = 0;

		List<Struct> structArList = structList.structList();

		int structListSz = structArList.size();

		for (int i = 0; i < structListSz; i++) {
			Struct curStruct = structArList.get(i);

			//double ownScore = curStruct.score();
			// create appropriate right and left Node's

			MatrixPathNode curMxPathNode = new MatrixPathNode(curStruct);
			
			// put path into corresponding Struct, the score along the path
			// down from here
			double pathScore = ArrayDFS(curMxPathNode);
			curStruct.set_maxDownPathScore(pathScore);

			if (pathScore > highestDownScore) {
				highestDownScore = pathScore;
				highestDownScoreIndex = i;
			}
			System.out.println("pathScore: " + pathScore);
		}

		structList.set_highestDownScoreIndex(highestDownScoreIndex);
		return highestDownScoreIndex;
	}

	/**
	 * depth-first-search with arrays construct Keep track of path via tree of
	 * MatrixPathNode's, and the scores thus far through the tree
	 * 
	 * @param mxPathNode
	 *            MatrixPathNode, corresponding to current Struct
	 * @return score
	 */
	// combine iteration of arraylist and recursion
	public static double ArrayDFS(MatrixPathNode mxPathNode) {

		Struct mxPathNodeStruct = mxPathNode.curStruct();
		StructList structList = mxPathNodeStruct.get_StructList();

		// highest down score encountered so far in list
		double highestDownScore = 0;
		// System.out.println(mxPathNodeStruct);
		// if structList == null at this point, it means the Struct is leaf, so
		// can return
		if (structList == null)
			return 1;

		int highestDownScoreIndex = structList.highestDownScoreIndex();
		// Iterator<Struct> structListIter = structList.iterator();

		// maintain index in list of highest score
		// don't iterate through if scores already computed
		if (highestDownScoreIndex == -1) {
			List<Struct> structArList = structList.structList();

			int structListSz = structArList.size();
			// int tempHighestDownScoreIndex = 0;

			// score so far along this path corresponding to this Node (not
			// Struct!)
			// double scoreSoFar = mxPathNode.scoreSoFar();

			for (int i = 0; i < structListSz; i++) {

				Struct struct = structArList.get(i);

				double structScore = struct.score();
				// highest score down from this Struct
				double tempDownScore = structScore;

				// don't like instanceof here
				if (struct instanceof StructA) {

					// System.out.print(struct.type());
					// System.out.print("[");
					// don't know type at compile time
					if (struct.prev1() instanceof Struct) {
						// create new MatrixPathNode,
						// int index, double ownScore, double scoreSoFar,

						// leftMxPathNode corresponds to Struct struct.prev1()
						MatrixPathNode leftMxPathNode = new MatrixPathNode((Struct) struct.prev1());
						tempDownScore *= ArrayDFS(leftMxPathNode);
					}

					//
					if (struct.prev2() instanceof Struct) {
						// System.out.print(", ");
						// dfs((Struct) struct.prev2());
						// construct new MatrixPathNode for right child
						MatrixPathNode rightMxPathNode = new MatrixPathNode((Struct) struct.prev2());
						tempDownScore *= ArrayDFS(rightMxPathNode);
					}

					// reached leaf. Add score to mxPathNode being passed in,
					// return own score
					if (struct.prev1() instanceof String) {
						// System.out.print(struct.prev1());
						// do nothing because String leaves don't count towards
						// score
					}
					if (struct.prev2() instanceof String) {

						// if (!struct.prev2().equals(""))
						// System.out.print(", ");
						// System.out.print(struct.prev2());
					}

					// System.out.print("]");
				} else if (struct instanceof StructH) {

					// System.out.print(struct.toString());
					List<Struct> childrenStructList = struct.children();

					if (childrenStructList != null && childrenStructList.size() > 0) {
						// return 1;

						// System.out.print("[");
						for (int j = 0; j < childrenStructList.size(); j++) {
							// System.out.print(childRelation.get(j) + " ");
							// dfs(childrenStructList.get(j));

							Struct childStruct = childrenStructList.get(j);

							// double curStructScore = childStruct.score();
							// create MatrixPathNode for each child Struct
							MatrixPathNode childMxPathNode = new MatrixPathNode(childStruct);

							tempDownScore *= ArrayDFS(childMxPathNode);
						}
					}
					// System.out.print("]");
				}
				if (tempDownScore > highestDownScore) {
					highestDownScore = tempDownScore;
					highestDownScoreIndex = i;
				}

				mxPathNodeStruct.set_maxDownPathScore(tempDownScore);

			} // end for loop through structList

			// set highestDownScoreIndex
			structList.set_highestDownScoreIndex(highestDownScoreIndex);
		} else {
			highestDownScoreIndex = structList.highestDownScoreIndex();
			highestDownScore = structList.structList().get(highestDownScoreIndex).maxDownPathScore();

		}
		return highestDownScore;
	}

	/**
	 * Write unknown words to file to classify them.
	 * 
	 * @throws IOException
	 */
	public static void writeUnknownWordsToFile() {
		try{
			Files.write(unknownWordsFile, unknownWords, Charset.forName("UTF-8"));
		}catch(IOException e){
			e.printStackTrace();
			throw new RuntimeException(e);
			
		}
	}

	/**
	 * write unknown words to file to classify them.
	 * Resets parsedExpr List by clearing.
	 * @throws IOException
	 */
	public static void writeParsedExprToFile() throws IOException {		
		List<String> parsedExprStringList = new ArrayList<String>();
		//just call .toString() directly on parsedExpr!
		for(ParsedPair parsedPair : parsedExpr){
			parsedExprStringList.add(parsedPair.toString());
		}
		Files.write(parsedExprFile, parsedExprStringList, Charset.forName("UTF-8"));
		parsedExpr = new ArrayList<ParsedPair>();
	}
	
	/**
	 * @return the List of parsed expressions, with different scores.
	 * Resets parsedExpr by re-initializing.
	 * Defensively copies List and returns copy.
	 */
	public static List<ParsedPair> getParsedExpr(){
		ImmutableList<ParsedPair> parsedExprCopy = ImmutableList.copyOf(parsedExpr);
		
		parsedExpr = new ArrayList<ParsedPair>();
		return parsedExprCopy;
	}
	
	/**
	 * Should be called once per parse segment to capture value,
	 * for each String in return array of ThmP1.preprocess(input).
	 * @return the context vector of highest-ranking parse.
	 */
	public static int[] getParseContextVector(){
		//return null if context vec not meaningful! Like for 2 terms.
		int[] parseContextVectorCopy = Arrays.copyOf(parseContextVector, parseContextVectorSz);
		//parseContextVector = new int[parseContextVectorSz];
		return parseContextVectorCopy;
	}
	
	/** 
	 * @return The ParseStruct parts of each parse since last retrieval.
	 */
	public static List<String> getParseStructMapList(){		
		//List<String> parseStructMapListCopy = new ArrayList<String>(parseStructMapList);
		ImmutableList<String> parseStructMapListCopy = ImmutableList.copyOf(parseStructMapList);
		parseStructMapList = new ArrayList<String>();;
		return parseStructMapListCopy;
	}
	
	/** 
	 * This method iterates through the lists of parseStructMaps.
	 * Should *only* be used for unit testing. Otherwise, should build separate field
	 * to avoid iterating here.
	 * @return The ParseStruct ParsedPairs of each parse since last retrieval.
	 */
	public static List<Multimap<ParseStructType, String>> getParseStructMaps(){		
		
		List<Multimap<ParseStructType, String>> parseStructStringList = new ArrayList<Multimap<ParseStructType, String>>();
		//get parsedStr in each parsedPair
		for(Multimap<ParseStructType, ParsedPair> map : parseStructMaps){
			Multimap<ParseStructType, String> newMap = ArrayListMultimap.create();
			for(Map.Entry<ParseStructType, ParsedPair> entry : map.entries()){
				newMap.put(entry.getKey(), entry.getValue().parsedStr);
			}
			parseStructStringList.add(newMap);
		}		
		return parseStructStringList;
	}
	
	/**
	 * if not full parse, try to make into full parse by fishing out the
	 * essential sentence structure, and discarding the phrases still not
	 * labeled after 2nd round
	 *
	 * @param parsedStructList
	 *            is list output by first round of parsing
	 */
	public static void parse2(List<Struct> inputList) {

	}

	private static int dfs(Struct struct, StringBuilder parsedSB, int span) {
		
		int structDepth = struct.dfsDepth();
		
		// don't like instanceof here
		if (struct instanceof StructA) {

			System.out.print(struct.type());
			parsedSB.append(struct.type());
			
			System.out.print("[");
			parsedSB.append("[");
			
			if (struct.prev1() instanceof Struct) {
				Struct prev1Struct = (Struct) struct.prev1();
				prev1Struct.set_dfsDepth(structDepth + 1);
				span = dfs(prev1Struct, parsedSB, span);
			}			

			if (struct.prev1NodeType().equals(NodeType.STR)) {
				System.out.print(struct.prev1());
				parsedSB.append(struct.prev1());
				//don't include prepositions for spanning purposes, since prepositions are almost 
				//always counted if its subsequent entity is, but counting it gives false high span
				//scores, especially compared to the case when they are absorbed into a StructH, in
				//which case they are not counted.
				if(!struct.type().equals("pre")){
					span++;
				}
			}			

			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (struct.prev2NodeType().isTypeStruct()) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				System.out.print(", ");
				parsedSB.append(", ");
				Struct prev2Struct = (Struct) struct.prev2();
				prev2Struct.set_dfsDepth(structDepth + 1);
				span = dfs(prev2Struct, parsedSB, span);
			}
			
			if (struct.prev2NodeType().equals(NodeType.STR)) {
				if (!struct.prev2().equals("")){
					System.out.print(", ");
					parsedSB.append(", ");	
					if(!struct.type().equals("pre")){
						span++;
					}
				}				
				System.out.print(struct.prev2());
				parsedSB.append(struct.prev2());
			}

			System.out.print("]");
			parsedSB.append("]");
		} else if (!struct.isStructA()) {
			
			System.out.print(struct.toString());
			parsedSB.append(struct.toString());
			span++;
			
			List<Struct> children = struct.children();
			List<String> childRelation = struct.childRelation();
			
			if (children == null || children.size() == 0)
				return span;

			System.out.print("[");
			parsedSB.append("[");
						
			for (int i = 0; i < children.size(); i++) {
				
				System.out.print(childRelation.get(i) + " ");
				parsedSB.append(childRelation.get(i) + " ");
				
				Struct child_i = children.get(i);
				child_i.set_dfsDepth(structDepth + 1);
				
				span = dfs(child_i, parsedSB, span);
			}
			System.out.print("]");
			parsedSB.append("]");
		}
		return span;
	}

	/**
	 * Preprocess. Remove fluff words. the, a, an
	 * 
	 * @param str
	 *            is string of all input to be processed.
	 * @return array of sentence Strings
	 */
	public static String[] preprocess(String inputStr) {

		ArrayList<String> sentenceList = new ArrayList<String>();
		//separate out punctuations, separate out words away from punctuations.
		//compile this!
		String[] wordsArray = inputStr.replaceAll("([^\\.,!:;]*)([\\.,:!;]{1})", "$1 $2").split("\\s+");
		//System.out.println("wordsArray " + Arrays.toString(wordsArray));
		int wordsArrayLen = wordsArray.length;

		StringBuilder sentenceBuilder = new StringBuilder();
		// String newSentence = "";
		String curWord;
		// whether in latex expression
		boolean inTex = false; 
		boolean madeReplacement = false;
		boolean toLowerCase = true;
		// whether in parenthetical remark
		boolean inParen = false; 
		
		String lastWordAdded = "";
		
		for (int i = 0; i < wordsArrayLen; i++) {

			curWord = wordsArray[i];

			//eliminate conditional-fluff words: words that are 
			//fluff only when occurring at certain places.
			/*if(curWord.matches("so")){
				if(i==0){
					continue;
				}
			}*/
			//compile these!
			if (!inTex) {
				if (inParen && curWord.matches("[^)]*\\)")) {
					inParen = false;
					continue;
				}else if (inParen || curWord.matches("\\([^)]*\\)")) {
					continue;
				} else if (curWord.matches("\\([^)]*")) {
					inParen = true;
					continue;
				} 
			}

			if (!inTex && curWord.matches("\\$.*") && !curWord.matches("\\$[^$]+\\$.*")) {
				inTex = true;
			} else if (inTex && curWord.contains("$")) {
				// }else if(curWord.matches("[^$]*\\$|\\$[^$]+\\$.*") ){
				inTex = false;
				toLowerCase = false;
			} else if (curWord.matches("\\$[^$]+\\$.*")) {
				toLowerCase = false;
			}

			// fluff phrases all start in posMap
			String curWordLower = curWord.toLowerCase();
			if (posMMap.containsKey(curWordLower)) {
				String pos = posMMap.get(curWordLower).get(0);
				String[] posAr = pos.split("_");
				String tempWord = curWord;

				int j = i;
				// potentially a fluff phrase <--improve defluffing!
				if (posAr[posAr.length - 1].equals("comp") && j < wordsArrayLen - 1) {
					// keep reading in string characters, until there is no
					// match.
					tempWord += " " + wordsArray[++j];
					
					while (posMMap.containsKey(tempWord.toLowerCase()) && j < wordsArrayLen - 1) {
						tempWord += " " + wordsArray[++j];
					}
					
					//**tempWord sometimes invokes too many strings!***** <--when?
					//System.out.println("tempWord: " + tempWord);					
					
					String replacement = fluffMap.get(tempWord.toLowerCase());
					if (replacement != null) {
						sentenceBuilder.append(" " + replacement);
						lastWordAdded = replacement;
						madeReplacement = true;
						i = j;
					}
					// curWord += wordsArray[++i];
				}
			}

			// if composite fluff word ?? already taken care of
			if (!madeReplacement && !curWord.matches("\\.|,|!") && !fluffMap.containsKey(curWord.toLowerCase())) {
				// if (!curWord.matches("\\.|,|!") &&
				// !fluffMap.containsKey(curWord)){
				if (inTex || !toLowerCase) {
					sentenceBuilder.append(" " + curWord);
					lastWordAdded = curWord;
					toLowerCase = true;
				} else{
					String wordToAdd = curWord.toLowerCase();
					sentenceBuilder.append(" " + wordToAdd);
					lastWordAdded = wordToAdd;
				}
			}
			madeReplacement = false;
			//compile!
			if (curWord.matches("\\.|,|!|;") || i == wordsArrayLen - 1) {
				//if not in middle of tex
				if (!inTex) {
					//if the token before and after the comma 
					//are not similar enough.
					if(curWord.matches(",") && i < wordsArrayLen - 1){
						//get next word
						String nextWord = wordsArray[i+1].toLowerCase();
						List<String> nextWordPosList = posMMap.get(nextWord);
						//e.g. $F$ is a ring, with no nontrivial elements. 
						if(!nextWordPosList.isEmpty() && nextWordPosList.get(0).equals("pre")
								|| AND_OR_PATTERN.matcher(nextWord).find()){
							//don't add curWord in this case, let the sentence continue.
							continue;
						}
						//else, because we don't want 
						else if(WordForms.areWordsSimilar(lastWordAdded, nextWord)){
							//substitute the comma with "and"
							sentenceBuilder.append(" and ");
							continue;
						}						
					}					
					sentenceList.add(sentenceBuilder.toString());
					sentenceBuilder.setLength(0);
					
				} else {
					lastWordAdded = curWord;
					sentenceBuilder.append(curWord);
				}
			}
		}
		if(sentenceList.isEmpty()){
			sentenceList.add(sentenceBuilder.toString());
		}
		return sentenceList.toArray(new String[0]);
	}

}
