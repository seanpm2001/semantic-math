package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.Maps;
import thmp.ThmP1;
import thmp.search.SearchWordPreprocess.WordWrapper;
import thmp.utils.WordForms;
import thmp.utils.WordForms.WordFreqComparator;

public class TriggerMathThm2 {

	/**
	 * Functionally equivalent to TriggerMathThm, but internally the term-thm map is built 
	 * differently. Instead of term -> theorems correspondence, we have theorem -> terms.
	 * Take inner product of trigger words and matrix of keywords. A column
	 * contains a set of words that trigger a particular MathObj, such as radius
	 * of convergence and root likely in column for function. And ideals for
	 * column for ring.
	 * 
	 * @author yihed
	 *
	 */

	/* List of mathObj's, in order they are inserted into mathObjMx */
	private static final List<String> mathObjList;
	
	private static final List<String> webDisplayThmList;
	
	/**
	 * Dictionary of keywords -> their index/row number in mathObjMx.
	 * ImmutableMap. Java indexing, starts at 0.
	 */
	private static final Map<String, Integer> keywordIndexDict;
	
	//private static final int LIST_INDEX_SHIFT = 1;
	/**
	 * Map of math objects, eg function, field, with list of keywords. don't
	 * need this either
	 */
	// private static final Multimap<String, String> mathObjMultimap;
	
	/* Matrix of keywords. */
	//private static final int[][] mathObjMx;
	private static final double[][] mathObjMx;
	//list of thms, same order as in thmList, and the frequencies of their words in words maps.
	private static final List<ImmutableMap<String, Integer>> thmWordsMapList;
	private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
	
	/**
	 * Debug variables
	 */
	private static final boolean DEBUG = false;
	
	static {
		//List of theorems, each of which
		//contains map of keywords and their frequencies in this theorem.
		thmWordsMapList = CollectThm.ThmWordsMaps.get_thmWordsFreqListNoAnno();
		
		// ImmutableList.Builder<String> keywordList = ImmutableList.builder();
		List<String> keywordList = new ArrayList<String>();
		
		// which keyword corresponds to which index	in the keywords list
		// ImmutableMap.Builder<String, Integer> keyDictBuilder = ImmutableMap.builder();
		//map of String (keyword), and integer (index in keywordList) of keyword.
		Map<String, Integer> keywordIndexMap = new HashMap<String, Integer>();
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		//to be list that contains the theorems, in the order they are inserted
		//ImmutableList.Builder<String> mathObjListBuilder = ImmutableList.builder();
		
		// math object pre map. keys are theorems, values are keywords in that thm.
		Multimap<String, String> mathObjMMap = ArrayListMultimap.create();

		//adds thms from CollectThm.thmWordsList. The thm name is its index in thmWordsList.
		addThmsFromList(keywordList, keywordIndexMap, mathObjMMap);
		//should already been ordered in CollectThm
		Map<String, Integer> docWordsFreqMapNoAnno = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
		
		//re-order the list so the most frequent words appear first, as optimization
		//so that search words can match the most frequently-occurring words.
		/*WordFreqComparator comp = new WordFreqComparator(docWordsFreqMapNoAnno);
		//words and their frequencies in wordDoc matrix.
		Map<String, Integer> keyWordIndexTreeMap = new TreeMap<String, Integer>(comp);
		keyWordIndexTreeMap.putAll(keywordIndexMap);*/
		
		keywordIndexDict = ImmutableMap.copyOf(docWordsFreqMapNoAnno);
		//System.out.println("!_-------keywordFreqDict: " + keywordFreqDict);
		//keywordIndexDict = ImmutableMap.copyOf(keywordIndexMap);
		
		//mathObjMx = new int[keywordList.size()][mathObjMMap.keySet().size()];	
		//mathObjMx = new int[keywordList.size()][thmWordsList.size()];
		mathObjMx = new double[keywordList.size()][thmWordsMapList.size()];
		//System.out.println("mathObjMx dims: " + keywordList.size() + " " + thmWordsMapList.size());
		ImmutableList<String> thmList = CollectThm.ThmList.allThmsWithHypList();
		
		//System.out.println("BEFORE mathObjMMap" +mathObjMMap);
		//pass in thmList to ensure the right order (insertion order) of thms 
		//is preserved or MathObjList and mathObjMMap. Multimaps don't preserve insertion order
		buildMathObjMx(//keywordList, 
				mathObjMMap, thmList);

		mathObjList = ImmutableList.copyOf(thmList);
		
		webDisplayThmList = ImmutableList.copyOf(CollectThm.ThmList.get_webDisplayThmList());
		
		/*for(int i = 0; i < thmWordsList.size(); i++){
			System.out.println(mathObjList.get(i));
			System.out.println(thmWordsList.get(i));
		}*/
	}
	
	
	/**
	 * Add keyword/term to mathObjList, in the process of building keywordList, mathObjMMap, etc.
	 * @param keywords
	 * @param keywordList
	 * @param keywordIndexMap
	 * @param mathObjMMap
	 */
	public static void addKeywordToMathObj(String[] keywords, List<String> keywordList,
			Map<String, Integer> keywordIndexMap, Multimap<String, String> mathObjMMap) {
		
		if (keywords.length == 0){
			return;		
		}
		
		//theorem
		String thm = keywords[0];
		//System.out.println("THM " + thm);
		for(int i = 1; i < keywords.length; i++){
			String keyword = keywords[i];
			mathObjMMap.put(thm, keyword); //version with tex, unprocessed
			//add each keyword in. words should already have annotation.
			if(!keywordIndexMap.containsKey(keyword)){
				keywordIndexMap.put(keyword, keywordList.size());
				keywordList.add(keyword);
			}			
		}		
		//System.out.println("AFTER mathObjMMap" + mathObjMMap);
		/*keyDictBuilder.put(keyword, keywordList.size());
		//*keyDictBuilder.put(keyword, keywordList.size());
		//System.out.println("Building: " + keyword);
		
		keywordList.add(keywords[0]);		
		
		for (int i = 1; i < keywords.length; i++) {
			//keys are theorems, values are keywords
			mathObjMMap.put(keywords[i], keyword);
			//mathObjMMap.put(thm, keywords[i]);
		}*/
	}

	/**
	 * Adds thms by calling addKeywordToMathObj on CollectThm.thmWordsList.
	 * Weighed by frequencies.
	 * @param keywords
	 * @param keywordList
	 * @param keywordIndexMap
	 * @param mathObjMMap
	 */
	private static void addThmsFromList(List<String> keywordList,
			Map<String, Integer> keywordIndexMap, Multimap<String, String> mathObjMMap){
		//thmWordsList has annotations, such as hyp or stm
		//ImmutableList<ImmutableMap<String, Integer>> thmWordsList = CollectThm.get_thmWordsListNoAnno();
		//System.out.println("---thmWordsList " + thmWordsList);
		ImmutableList<String> thmList = CollectThm.ThmList.allThmsWithHypList();
		//System.out.println("---thmList " + thmList);
		
		//index of thm in thmWordsList, to be used as part of name
		//thm words and their frequencies.
		int thmIndex = 0;
		//key is thm, values are indices of words that show up in thm.
		for(ImmutableMap<String, Integer> wordsMap : thmWordsMapList){
			
			//make whole thm text as name <--should make the index the key!			
			String thmName = thmList.get(thmIndex++);
			//System.out.println("!thmName " +thmName);
			List<String> keyWordsList = new ArrayList<String>();
			keyWordsList.add(thmName);
			keyWordsList.addAll(wordsMap.keySet());
			//System.out.println("wordsMap " +wordsMap);
			//thms added to mathObjMMap are *not* in the same order as thms are iterated here! Because Multimaps don't 
			//need to preserve insertion order!
			addKeywordToMathObj(keyWordsList.toArray(new String[keyWordsList.size()]), keywordList, keywordIndexMap, mathObjMMap);
		}
	}
	
	/**
	 * Builds the MathObjMx. Weigh inversely based on word frequencies extracted from 
	 * CollectThm.java.
	 */
	private static void buildMathObjMx(//List<String> keywordList, 
			Multimap<String, String> mathObjMMap,
			ImmutableList<String> thmList) {
		
		//map of annotated words and their scores
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();
		
		//Iterator<String> mathObjMMapkeysIter = mathObjMMapkeys.iterator();
		Iterator<String> thmListIter = thmList.iterator();
		
		int mathObjCounter = 0;
		
		while (thmListIter.hasNext()) {
			String thm = thmListIter.next();
			//String curMathObj = mathObjMMapkeysIter.next();
			//System.out.println("BUILDING mathObjList " +curMathObj);
			//mathObjListBuilder.add(thm);
			Collection<String> curMathObjCol = mathObjMMap.get(thm);
			//Iterator<String> curMathObjColIter = curMathObjCol.iterator();
			double norm = 0;
			for (String keyword : curMathObjCol) {
				//Integer keyWordIndex = keywordDict.get(keyword);
				Integer wordScore = wordsScoreMap.get(keyword);
				norm += Math.pow(wordScore, 2);
				//should not be null, as wordsScoreMap was created using same list of thms.
				//if(wordScore == null) continue;
				//weigh each word based on *local* frequency, ie word freq in sentence, not whole doc.
				//mathObjMx[keyWordIndex][mathObjCounter] = wordScore;
			}
			//Sqrt is usually too large, so take log instead of sqrt.
			//norm = norm < 3 ? 1 : (int)Math.sqrt(norm);
			norm = Math.sqrt(norm);
			//divide by log of norm
			for (String keyword : curMathObjCol) {
				Integer keyWordIndex = keywordIndexDict.get(keyword);
				Integer wordScore = wordsScoreMap.get(keyword);
				//divide by log of norm
				//could be very small! ie 0 after rounding.
				double newScore = (double)wordScore/norm;
				//int newScore = wordScore;
				if(newScore == 0 && wordScore != 0){
					mathObjMx[keyWordIndex][mathObjCounter] = .1;
				}else{
					mathObjMx[keyWordIndex][mathObjCounter] = newScore;
				}
			}
			mathObjCounter++;
		}
		//System.out.println("~~keywordDict "+keywordDict);
	}

	/**
	 * Create query row vector, 1's and 0's.
	 * @deprecated SVD queries should be created without word annotations.
	 * @param triggerTerms
	 * @return String representation of query vector, eg {{1,0,1,0}}
	 */
	public static String createQuery(String thm){
		
		//String[] thmAr = thm.split("\\s+|,|;|\\.");
		//map of annotated words and their scores
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();		
		//should eliminate unnecessary words first, then send to get wrapped.
		//<--can only do that if leave the hyp words in, eg if.
		List<WordWrapper> wordWrapperList = SearchWordPreprocess.sortWordsType(thm);
		//System.out.println(wordWrapperList);
		//keywordDict is annotated with "hyp"/"stm"
		int dictSz = keywordIndexDict.keySet().size();
		int[] triggerTermsVec = new int[dictSz];
		
		for (WordWrapper wordWrapper : wordWrapperList) {
			//annotated form
			String termAnno = wordWrapper.hashToString();
			
			Integer rowIndex = keywordIndexDict.get(termAnno);
			//System.out.println("first rowIndex " + rowIndex);
			if(rowIndex == null){
				termAnno = wordWrapper.otherHashForm();
				rowIndex = keywordIndexDict.get(termAnno);				
			}
			//should normalize!!
			
			//System.out.println("second rowIndex " + rowIndex);
			if (rowIndex != null) {
				int termScore = wordsScoreMap.get(termAnno);
				//System.out.println("termAnno " + termAnno);
				//System.out.print("termScore " + termScore + "\t");
				//triggerTermsVec[rowIndex] = termScore;
				//keywordDict starts indexing from 0!
				triggerTermsVec[rowIndex] = termScore;
			}
		}
		//transform into query list String 
		StringBuilder sb = new StringBuilder();
		sb.append("{{");
		//String s = "{{";
		for(int j = 0; j < dictSz; j++){
			String t = j == dictSz-1 ? triggerTermsVec[j] + "" : triggerTermsVec[j] + ", ";
			sb.append(t);
		}
		sb.append("}}");
		System.out.println("query vector " + sb);
		return sb.toString();
	}

	/**
	 * Same as creareQuery, no annotation.
	 * Create query row vector, weighed using word scores,
	 * and according to norm. To be compared to columns 
	 * of term document matrix.
	 * @param thm
	 * @return
	 */
	public static String createQueryNoAnno(String thm){
		
		//String[] thmAr = thm.split("\\s+|,|;|\\.");
		String[] thmAr = thm.split(WordForms.splitDelim());
		//map of non-annotated words and their scores. Use get_wordsScoreMapNoAnno 
		//and not CONTEXT_VEC_WORDS_MAP.
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();	
		//System.out.println("wordsScoreMap inside TriggerMathThm: " + wordsScoreMap);
		//should eliminate unnecessary words first, then send to get wrapped.
		//<--can only do that if leave the hyp words in, eg if.
		
		String priorityWords = ConstantsInSearch.get_priorityWords();
		//keywordDict is annotated with "hyp"/"stm"
		int dictSz = keywordIndexDict.keySet().size();
		//int[] triggerTermsVec = new int[dictSz];
		double[] queryVec = new double[dictSz];
		double norm = 0;
		//highest weight amongst the single words
		double highestWeight = 0;
		//int numWords = 0;
		List<Integer> priorityWordsIndexList = new ArrayList<Integer>();
		
		//get norm first, form unnormalized vector, then divide terms by log of norm
		for (int i = 0; i < thmAr.length; i++) {
			String term = thmAr[i];
			double newNorm;
			newNorm = addToNorm(thmAr, wordsScoreMap, queryVec, norm, i, term);
			if(newNorm - norm > highestWeight){
				highestWeight = newNorm - norm;
			}
			if(i < thmAr.length-1){
				String nextTermCombined = term + " " + thmAr[i+1];
				newNorm = addToNorm(thmAr, wordsScoreMap, queryVec, newNorm, i, nextTermCombined);	
				//System.out.println("combined word: " + nextTermCombined + ". norm: " + newNorm);
				
				if(i < thmAr.length-2){
					String threeTermsCombined = nextTermCombined + " " + thmAr[i+2];
					newNorm = addToNorm(thmAr, wordsScoreMap, queryVec, newNorm, i, threeTermsCombined);
				}
			}
			if(term.matches(priorityWords)){
				priorityWordsIndexList.add(i);
			}						
			norm = newNorm;
		}
		//short-circuit if no relevant term was detected in input thm
		//rather than return a list of results that are close to the 0-vector
		//but doesn't make sense.
		if(norm == 0) return "";
		//fill in the priority words with highestWeight
		for(int index : priorityWordsIndexList){
			Integer rowIndex = keywordIndexDict.get(thmAr[index]);
			if(rowIndex != null){
				double prevWeight = queryVec[rowIndex];
				double weightDiff = highestWeight - prevWeight;
				queryVec[rowIndex] = highestWeight;
				norm += weightDiff;
			}
		}		
		//avoid division by 0 apocalypse, when it was log
		
		//norm = norm < 3 ? 1 : (int)Math.log(norm);
		norm = Math.sqrt(norm);
		norm = norm == 0 ? 1 : norm;
		//divide entries of triggerTermsVec by norm
		for(int i = 0; i < queryVec.length; i++){
			double prevScore = queryVec[i]; 
			queryVec[i] = queryVec[i]/norm;
			
			//avoid completely obliterating a word 
			if(prevScore != 0 && queryVec[i] == 0){
				//triggerTermsVec[i] = 1;
				queryVec[i] = .1;
			}
		}
		
		//transform into query list String 
		StringBuilder sb = new StringBuilder();
		sb.append("{{");
		for(int j = 0; j < dictSz; j++){
			String t = j == dictSz-1 ? queryVec[j] + "" : queryVec[j] + ", ";
			sb.append(t);
		}
		sb.append("}}");
		System.out.println("query vector " + sb);
		return sb.toString();
	}

	/**
	 * Auxiliary method to createQueryNoAnno. Adds word weight
	 * to norm, when applicable.
	 * @param thmAr
	 * @param wordsScoreMap
	 * @param norm
	 * @param i
	 * @param term
	 * @return
	 */
	private static double addToNorm(String[] thmAr, Map<String, Integer> wordsScoreMap, double[] triggerTermsVec,
			double norm, int i, String term) {
		Integer termScore = wordsScoreMap.get(term);
		//get singular forms		
		if(termScore == null){
			term = WordForms.getSingularForm(term);
			termScore = wordsScoreMap.get(term);
		}
		//System.out.println("term: " + term + ". termScore: " + termScore );
		
		//triggerTermsVec[rowIndex] = termScore;
		//keywordDict starts indexing from 0!
		Integer rowIndex = keywordIndexDict.get(term);
		
		if(termScore != null && rowIndex != null){
			//just adding the termscore, without squaring, works better
			//norm += Math.pow(termScore, 2);
			norm += Math.pow(termScore, 2);
			thmAr[i] = term;
			//shouldn't be null, since termScore!=null
			//int rowIndex = keywordDict.get(term);
			//triggerTermsVec[rowIndex] = termScore;
			//keywordDict starts indexing from 0!
			triggerTermsVec[rowIndex] = termScore;
			System.out.println("TriggerMathThm2.java: term just added: " + term + ". " + rowIndex + ". termScore: " + termScore);
			//System.out.println(Arrays.toString(triggerTermsVec));
		}
		return norm;
	}

	/**
	 * Obtains mathThmMx
	 * @return
	 */
	public static double[][] mathThmMx(){
		return mathObjMx;
	}
	
	/**
	 * Map of words to their corresponding row index in term-document matrix.
	 * This has been ordered, such that more frequently used words fall to the 
	 * beginning when iterating through the map.
	 * This is ordered version of CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno().
	 * @return
	 */
	/*public static Map<String, Integer> allThmsKeywordIndexDict(){
		return keywordIndexDict;
	}*/

	public static int keywordDictSize(){
		return keywordIndexDict.size();
	}
	
	/**
	 * Get theorem given its index (column number) in mathThmMx. Not the displayed web version.
	 * @param index 0-based index
	 * @return
	 */
	public static String getThm(int index){
		//System.out.println("docWrodsFreqMap " + docWordsFreqMap);
		System.out.print("Thm index: " + index + "\t");
		//index is 1-based indexing, not 0-based.
		//System.out.println(CollectThm.get_thmWordsListNoAnno().get(index-1));
		
		if(DEBUG){
			Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();		
			for(String word : thmWordsMapList.get(index-1).keySet()){
				System.out.print(word + " " + wordsScoreMap.get(word) + " " + docWordsFreqMapNoAnno.get(word));
			}
		}
		//System.out.println(thmWordsList.get(index-1));
		//return mathObjList.get(index-LIST_INDEX_SHIFT);
		return mathObjList.get(index);
	}
	
	/**
	 * Get theorem given its index (column number) in mathThmMx. For web display.
	 * without \index, \label etc.
	 * @param index 0-based index
	 * @return
	 */
	public static String getWebDisplayThm(int index){
		//System.out.println("docWrodsFreqMap " + docWordsFreqMap);
		System.out.print("Thm index: " + index + "\t");
		//index is 1-based indexing, not 0-based.
		//System.out.println(CollectThm.get_thmWordsListNoAnno().get(index-1));
		//return webDisplayThmList.get(index-LIST_INDEX_SHIFT);
		return webDisplayThmList.get(index);
	}
	
	/*public static void main(String[] args){
		System.out.print(keywordDict.size());
	}*/
}
