package thmp.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import thmp.ThmP1;
import thmp.search.CollectFreqWords;
import thmp.search.WordFrequency;

public class WordForms {

	//delimiters to split on when making words out of input
	private static final String SPLIT_DELIM = "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:|-|_|~|!";
	private static final Pattern BACKSLASH_PATTERN = Pattern.compile("(?<!\\\\)\\\\(?!\\\\)");
	private static final Pattern BRACES_PATTERN = Pattern.compile("(\\{|\\}|\\[|\\])");
	//small lists of fluff words, used in, e.g., n gram extraction.
	//*don't* put "of" here, will interfere with 3 gram collection
	private static final String FLUFF_WORDS_SMALL = "a|the|tex|of|and|on|let|lemma|for|to|that|with|is|be|are|there|by"
			+ "|any|as|if|we|suppose|then|which|in|from|this|assume|this|have|just|may|an|every|it|between|given|itself|has"
			+ "|more";
	private static ImmutableSet<String> freqWordsSet; 
	//brackets pattern
	private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");	
	private static final Pattern LATEX_PATTERN = Pattern.compile("\\$([^$]+)\\$");
	
	private static Set<String> getFreqWordsSet(){
		if(freqWordsSet == null){
			synchronized(WordForms.class){
				if(freqWordsSet == null){
					freqWordsSet = WordFrequency.ComputeFrequencyData.get_FreqWords();
				}
			}
		}
		return freqWordsSet;
	}
	
	/**
	 * Returns the most likely singular form of the word, or
	 * original word if it doesn't end in s, es, or ies
	 * @param word
	 * @return
	 */
	public static String getSingularForm(String word){
		//if word in dictionary, should not be processed. Eg "continuous"
		if(getFreqWordsSet().contains(word)) return word;
		
		String[] singFormsAr = getSingularForms(word);
		//singFormsAr successively replaces words ending in "s", "es", "ies"
		int k = 2;
		while(k > -1 && singFormsAr[k] == null){
			k--;
		}
		if(k != -1){
			word = singFormsAr[k];
			//System.out.println("singular form: "+ word);
		}
		//if letter before "es" is not "s", eg "kisses", or "ch", eg "catches",
		//add e back, to preserve terms that end in "e", eg "agrees"
		String irregPluralEndings = "s|h|x";
		int wordLen = word.length();
		if(k==1 && wordLen > 1 && !String.valueOf(word.charAt(wordLen-1)).matches(irregPluralEndings)){
			word = word+"e";
		}
		return word;
	}
	
	/**
	 * Get the singular forms of current word
	 * @param curWord
	 * @param wordlen
	 * @return Array of singular forms
	 */
	public static String[] getSingularForms(String curWord) {
		// primitive way to handle plural forms: if ends in "s"
		String[] singularForms = new String[3];
		int wordlen = curWord.length();
		
		if (wordlen > 0 && curWord.charAt(wordlen - 1) == 's') {
			//don't strip 's' if belongs to common endings with 's', e.g. "homogeneous", "basis"
			if(!curWord.matches(".*ous$?|.*is$?|has")){ 
				singularForms[0] = curWord.substring(0, wordlen - 1);								
			}
		}

		if (wordlen > 2 && curWord.substring(wordlen - 2, wordlen).equals("es")) {
			singularForms[1] = curWord.substring(0, wordlen - 2);
		}

		if (wordlen > 3 && curWord.substring(wordlen - 3, wordlen).equals("ies")) {
			singularForms[2] = curWord.substring(0, wordlen - 3) + 'y';
		}
		return singularForms;
	}
	
	/**
	 * Make the fluff map from the fluff String.
	 */
	public static Set<String> makeFluffSet(){
		Set<String> fluffSet = new HashSet<String>();
		String[] fluffAr = FLUFF_WORDS_SMALL.split("\\|");
		for(String word : fluffAr){
			fluffSet.add(word);
		}
		return fluffSet;
	}
	
	/**
	 * Returns split delimiters.
	 * @return
	 */
	public static String splitDelim(){
		return SPLIT_DELIM;
	}
	
	/**
	 * Return brackets pattern: "\\[([^\\]]*)\\]"
	 * @return
	 */
	public static Pattern BRACKETS_PATTERN(){
		return BRACKETS_PATTERN;
	}
	
	
	public static enum TokenType{
		SINGLETON(1), TWOGRAM(2), THREEGRAM(3);
		int indexShift;
		static int TWO_GRAM_BONUS = 1;
		static int THREE_GRAM_BONUS = 1;
		
		TokenType(int shift){
			this.indexShift = shift;
		}
		
		public void addToMap(Multimap<Integer, Integer> thmWordSpanMMap, int thmIndex, int wordIndex){
			for(int i = wordIndex; i < wordIndex+indexShift; i++){
				thmWordSpanMMap.put(thmIndex, i);
			}
		}
		
		public int adjustNGramScore(int curScoreToAdd, int[] singletonScoresAr, int wordIndex){
			switch(this){
				case SINGLETON:
					return curScoreToAdd;
				case TWOGRAM:
					return Math.max(curScoreToAdd, singletonScoresAr[wordIndex] 
							+ singletonScoresAr[wordIndex+1] + TWO_GRAM_BONUS);
				case THREEGRAM:
					return Math.max(curScoreToAdd, singletonScoresAr[wordIndex] + singletonScoresAr[wordIndex+1]
							+ singletonScoresAr[wordIndex+2] + THREE_GRAM_BONUS);
				default:
					return curScoreToAdd;
			}
		}
		
		/**
		 * Whether current word at wordIndex has been added to thm already under a previous 2/3-gram.
		 * @param thmWordSpanMMap
		 * @param thmIndex
		 * @param wordIndex
		 */
		public boolean ifAddedToMap(Multimap<Integer, Integer> thmWordSpanMMap, int thmIndex, int wordIndex){
			
			if(this.equals(THREEGRAM)) return false;
			
			if(this.equals(TWOGRAM)){ 
				return thmWordSpanMMap.containsEntry(thmIndex, wordIndex)
						&& thmWordSpanMMap.containsEntry(thmIndex, wordIndex+1);
			}
			
			if(this.equals(SINGLETON)){ 
				return thmWordSpanMMap.containsEntry(thmIndex, wordIndex);
			}
			
			return false;
		}
		
	}
	
	/**
	 * Heuristic for determining if two latex expressions are similar 
	 * enough, to warrant them being grouped together via conjunction or
	 * disjunction. E.g. let $S$, $T$ be rings.
	 * Parameters *can* be wrapped inside $$ signs.
	 * Marks of similarity: 
	 * -one is a tilde or prime of another.
	 * @param tex1 
	 * @param tex2 
	 */
	public static boolean areWordsSimilar(String tex1, String tex2){
		
		Matcher tex1Matcher = LATEX_PATTERN.matcher(tex1);
		Matcher tex2Matcher = LATEX_PATTERN.matcher(tex2);
		//strip $ $ signs.
		if(tex1Matcher.find() && tex2Matcher.find()){
			tex1 = tex1Matcher.group(1);
			tex2 = tex1Matcher.group(1);
		}else{
			return false;
		}
		
		//if tex1 and tex2 have the same "form". E.g. S, T.
		if(tex1.length() < 3 && tex1.length() == tex2.length()){
			return true;
		}
		
		//if tex2 is a variation of tex1
		//create regex from one to match the other. Expand this!
		//turn '\' into "\\\\" to create legal regex
		//tex1 = BACKSLASH_PATTERN.matcher(tex1).replaceAll("\\\\");
		tex1 = BRACES_PATTERN.matcher(tex1).replaceAll("\\$1");
		tex1 = Matcher.quoteReplacement(tex1);
		String tex1Regex = "\\\\tilde\\{" + tex1 + "\\}|" + tex1 + "'";
		Pattern tex1Pattern = Pattern.compile(tex1Regex);
		if(tex1Pattern.matcher(tex2).find()){
			return true;
		}
		
		return false;
	}
	
}
