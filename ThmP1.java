import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;

/* 
 * contains hashtable of entities (keys) and properties (values)
 */

public class ThmP1 {
	
	private static HashMap<String, ArrayList<String>> entityMap;
	//map of structures, for all, disj,  etc
	private static HashMap<String, String> structMap;
	private static HashMap<String, String> anchorMap;
	//parts of speech map, e.g. "open", "adj"
	private static HashMap<String, String> posMap;
	//fluff words, e.g. "the", "a"
	private static HashMap<String, String> fluffMap;
		
	private static HashMap<String, String> mathObjMap;
	//map for composite adjectives, eg positive semidefinite
	//value is regex string to be matched
	private static HashMap<String, String> adjMap;
	
	private HashMap<String, StructH<HashMap<String, String>>> namesMap;
	
	//part of speech, last resort after looking up entity property maps
	//private static HashMap<String, String> pos;
	
	/* build hashmap
	 * 
	 */
	public static void buildMap(){
		entityMap = new HashMap<String, ArrayList<String>>();
		ArrayList<String> ppt = new ArrayList<String>();
		ppt.add("characteristic"); 
		entityMap.put("Field", ppt);		
		
		adjMap = new HashMap<String, String>();
		//value is regex string to be matched
		adjMap.put("semidefinite", "positive|negative");
		
		fluffMap = new HashMap<String, String>();
		fluffMap.put("the", "the");
		fluffMap.put("a", "a");
		
		//most should already be filtered in entity/property
		//pos should preserve connective words, or stays or
		posMap = new HashMap<String, String>();
		posMap.put("disjoint", "adj"); posMap.put("perfect", "adj");
		posMap.put("finite", "adj"); posMap.put("linear", "adj");
		//posMap.put("every", "every");		
		//prepositions
		posMap.put("or", "or"); posMap.put("and", "and"); 
		posMap.put("is", "is"); posMap.put("at", "pre"); posMap.put("if", "if");
		posMap.put("then", "then"); posMap.put("between", "between"); 
		//between... -> between, and...->and, between_and->between_and
		
		
		posMap.put("in", "pre");
		posMap.put("on", "pre"); posMap.put("let", "let"); posMap.put("be", "be");
		
		//verbs, verbs map does not support -ing form, ie divide->dividing
		//3rd person singular form that ends in "es" are checked with 
		//the "es" stripped
		posMap.put("divide", "verb"); posMap.put("extend", "verb");	
		
		//build in quantifiers into structures, forall (indicated
		//by for all, has)
		//entityMap.put("for all", null); //change null, use regex
		//entityMap.put("there exists", null); 
		
		//map for math objects
		mathObjMap = new HashMap<String, String>();
		mathObjMap.put("characteristic", "mathObj");
		mathObjMap.put("Fp", "mathObj"); mathObjMap.put("transformation", "mathObj");
		mathObjMap.put("ring", "mathObj"); 
		mathObjMap.put("field", "mathObj"); mathObjMap.put("function", "mathObj");
		//composite math objects
		mathObjMap.put("field", "COMP");
		mathObjMap.put("finite field", "mathObj"); mathObjMap.put("vector field", "mathObj");
		mathObjMap.put("vector", "mathObj"); mathObjMap.put("angle", "mathObj");

		
		//put in template matching, prepositions, of, by, with
		
		//conjunctions, and, or
		anchorMap = new HashMap<String, String>();	
		
		//anchors, contains   of, with
		anchorMap = new HashMap<String, String>();		
		anchorMap.put("of", "of");
		anchorMap.put("that is", "that is"); //careful with spaces		
		//local "or", ie or of the same types
		//anchorMap.put("or", "or"); 		
		
		//struct map, statement/global structures
		//can be written as, 
		structMap = new HashMap<String, String>();	
		structMap.put("for all", "forall");		
		structMap.put("is", "is");  //these not necessary any more
		structMap.put("are", "are"); 
		structMap.put("has", "has");
		
		//"is" implies assertion 
		structMap.put("is_symb", "is"); structMap.put("is_int", "is");
		structMap.put("is_ent", "is"); structMap.put("is_or", "is");
		structMap.put("ent_is", "assert");
		structMap.put("symb_is", "assert");
		structMap.put("if_assert", "ifstate");		
		structMap.put("then_assert", "thenstate");
		
		structMap.put("ifstate_thenstate", "ifthen");
		structMap.put("has_ent", "hasent");	
		structMap.put("or_ent", "orent"); structMap.put("ent_orent", "or");
		structMap.put("or_symb", "orsymb"); structMap.put("symb_orsymb", "or");
		structMap.put("or_assert", "orass"); structMap.put("ent_orass", "or");
		structMap.put("or_is", "assert");
		structMap.put("assert_orass", "or");
		structMap.put("ent_adj", "ent"); structMap.put("ent_ppt", "ent");		
		
		//preposition_entity, eg "over field", "on ...". Create new child in this case
		//latter ent child of first ent
		structMap.put("pre_ent", "preent"); structMap.put("ent_preent", "newchild");
		//participle: called
		structMap.put("parti_ent", "partient"); structMap.put("ent_partient", "newchild");
		structMap.put("from_   ", "from"); structMap.put("to_...   ", "to"); structMap.put("fromto", "newchild");

		//verb_ent, not including past tense verbs, only present tense
		structMap.put("verb_ent", "verbent"); structMap.put("ent_verbent", "assert");
		structMap.put("verb_symb", "verbphrase"); structMap.put("symb_verbphrase", "assert");
		structMap.put("let_symb", "let"); structMap.put("be_ent", "be"); structMap.put("let_be", "letbe");
		
		//for adding combinations to structMap
		ArrayList<String> structs = new ArrayList<String>();
		structs.add("has");
		
		//for loop constructing more structs to put in maps, e.g. entityhas, entityis
		for(int i = 0; i < structs.size(); i++){
			//entityhas, vs forall
			
		}

	}
	
	//keywords: given, with, 
	// has, is, 
	//if ... then
	public ArrayList<Struct> tokenize(String[] str){
		//go through list twice, build hashmaps second time		
		
		//tokenize first. compound words?
		//find the prepositions
		
		//find if, conditionals, 
		//first run through sentence, find structure
		//if.. then, something... is/has...
		
		//EntityProperty's found so far
		//...will include properties like symbol name in hashtable
		//hashmap contains, e.g. (char, p)

		//....let ... be, given... 
		//.....change to arraylist of Pairs, create Pair class
		//LinkedHashMap<String, String> linkedMap = new LinkedHashMap<String, String>();
		
		//list of indices of "proper" math objects, e.g. "field", but not e.g. "pair" 
		ArrayList<Integer> mathIndexList = new ArrayList<Integer>();
		//list of indices of anchor words, e.g. "of"
		ArrayList<Integer> anchorList = new ArrayList<Integer>();
		
		//list of given names, like F in "field F", for bookkeeping later
		//hashmap contains <name, entity> pairs
		//need to incorporate multi-letter names, like sigma
		namesMap = new HashMap<String, StructH<HashMap<String, String>>>();

		//list of each word with their initial type, adj, noun, 
		ArrayList<Pair> pairs = new ArrayList<Pair>();		
		
		for(int i = 0; i < str.length; i++){			
			
			int strlen = str[i].length();
			
			if(mathObjMap.containsKey(str[i])){
				String newObj = str[i];
				int k = 1;
				
				//if composite math noun, eg "finite field"
				while(i-k > -1 && mathObjMap.containsKey(str[i-k] +" "+ newObj) &&
						mathObjMap.get(str[i-k] +" "+ newObj).equals("mathObj")){
					
					//remove previous pair from pairs if it has 
					//new match
					//pairs.size should be > 0, ie previous word should be classified already
					if(pairs.size() > 0 && 
							pairs.get(pairs.size()-1).word().equals(str[i-k])){
						pairs.remove(pairs.size()-1);
					}
					
					newObj = str[i-k] + " " + newObj;
					k++;
				}
				Pair pair = new Pair(newObj, "mathObj");
				pairs.add(pair);
				mathIndexList.add(pairs.size()-1);
				
			}
			else if(anchorMap.containsKey(str[i])){
				Pair pair = new Pair(str[i], "anchor");
				anchorList.add(i);
				pairs.add(pair);
			}
			//if adjective
			else if(posMap.containsKey(str[i])){
				Pair pair = new Pair(str[i], posMap.get(str[i]));
				pairs.add(pair);
			}
			//check again for verbs ending in 'es' & 's'			
			else if(str[i].charAt(strlen-1) == 's'){
				
				if(posMap.containsKey(str[i].substring(0, strlen-1)) &&
						posMap.get(str[i].substring(0, strlen-1)).equals("verb")){					
					Pair pair = new Pair(str[i], "verb");
					pairs.add(pair);
				}else if(str[i].charAt(str[i].length()-2) == 'e' &&
						posMap.containsKey(str[i].substring(0, strlen-2)) &&
						posMap.get(str[i].substring(0, strlen-2)).equals("verb")){
					Pair pair = new Pair(str[i], "verb");
					pairs.add(pair);					
				}
			}
			//adverbs that end with -ly that haven't been screened off before
			else if(strlen > 1 && str[i].substring(strlen-2, strlen).equals("ly")){
				Pair pair = new Pair(str[i], "adverb");
				pairs.add(pair);
			}
			//participles and gerunds. Need special list for words such as "given"
			else if(strlen > 1 && str[i].substring(strlen-2, strlen).equals("ed")
					&& posMap.containsKey(str[i].substring(0, strlen-2))
					&& posMap.get(str[i].substring(0, strlen-2)).equals("verb") ){
				//if next word is "by"
				
				Pair pair = new Pair(str[i], "parti");
				pairs.add(pair);
			}
			else if(strlen > 2 && str[i].substring(strlen-3, strlen).equals("ing")
					&& posMap.containsKey(str[i].substring(0, strlen-2))
					//... take care of verbs ending in "e"
					&& posMap.get(str[i].substring(0, strlen-3)).equals("verb") ){
				Pair pair = new Pair(str[i], "gerund");
				pairs.add(pair);
			}
			else if(str[i].matches("[a-zA-Z]")){
				//variable/symbols
	
				Pair pair = new Pair(str[i], "symb");
				pairs.add(pair);
				
			}
			//Get numbers. Incorporate written-out numbers, eg "two"
			else if(str[i].matches("^//d+$")){
				Pair pair = new Pair(str[i], "num");
				pairs.add(pair);
			}else{ //try to minimize this case.
				//defluffing: not recognized phrases are not added
				System.out.println("word not in dictionary: " + str[i]);
			}
			
		}
		
		//map of math entities, has mathObj + ppt's
		ArrayList<StructH<HashMap<String, String>>> mathEntList = 
				new ArrayList<StructH<HashMap<String, String>>>();
		
		//second run, combine adj with math nouns
		for(int j = 0; j < mathIndexList.size(); j++){
			
			int index = mathIndexList.get(j);
			String mathObj = pairs.get(index).word();
			pairs.get(index).set_pos(String.valueOf(j));
			
			StructH<HashMap<String, String>> tempStructH =
					new StructH<HashMap<String, String>>("ent");
			HashMap<String, String> tempMap = new HashMap<String, String>();
			tempMap.put("name", mathObj);
			tempMap.put("type", "ent");
			
			//look right two places in pairs, if symbol found, add it to namesList 
			//if it's the given name for an ent. 
			int pairsSize = pairs.size();
			if(index + 1 < pairsSize && pairs.get(index+1).pos().equals("symb")){
				pairs.get(index+1).set_pos(String.valueOf(j));
				String givenName = pairs.get(index+1).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);
				
			}else if((pairsSize+2 < pairsSize && pairs.get(index+2).pos().equals("symb"))){
				pairs.get(index+2).set_pos(String.valueOf(j));
				String givenName = pairs.get(index+2).word();
				tempMap.put("called", givenName);
				namesMap.put(givenName, tempStructH);
			}			
			
			//look to left and right
			int k = 1;			
			//combine multiple adjectives into entities
			//...get more than adj ... multi-word descriptions
			//set the pos as the current index in mathEntList		
			
			while(index-k > -1 && pairs.get(index - k).pos().equals("adj") ){
				String curWord = pairs.get(index - k).word();
				//look for composite adj (two for now)
				if(index-k-1 > -1 && adjMap.containsKey(curWord)){
					//if composite adj
					if(pairs.get(index-k-1).word().matches(adjMap.get(curWord))){
						curWord = pairs.get(index-k-1).word() + " " + curWord;
						//mark pos field to indicate entity
						pairs.get(index - k).set_pos(String.valueOf(j));
						k++;
					}
				}
				
				tempMap.put(curWord, "ppt");				
				//mark the pos field in those absorbed pairs as index in mathEntList				
				pairs.get(index - k).set_pos(String.valueOf(j));				
				k++;
			}	
			
			//combine multiple adj connected by "and/or"
			while(index-k-1 > -1 && pairs.get(index - k).pos().matches("or|and")){
				if(pairs.get(index - k - 1).pos().equals("adj")){
					//set pos() of or/and to the right index
					pairs.get(index - k).set_pos(String.valueOf(j));
					String curWord = pairs.get(index - k - 1).word();
					tempMap.put(curWord, "ppt");	
					pairs.get(index - k - 1).set_pos(String.valueOf(j));	
				}
				k++;
			}
			
			//look forwards
			k = 1;
			while(index+k < pairs.size() && pairs.get(index + k).pos().equals("adj") ){				
				///implement same as above

				tempMap.put(pairs.get(index - k).word(), "ppt");
				pairs.get(index + k).set_pos(String.valueOf(j));
				k++;
			}			
			
			tempStructH.set_struct(tempMap);
			mathEntList.add(tempStructH);
		}
		
		//combine anchors into entities. Such as "of," "has"
		for(int j = 0; j < anchorList.size(); j++){
			int index = anchorList.get(j);
			String anchor = str[index];			
			
			//the expression before this anchor is an entity
			if(index > 0 && pairs.get(index - 1).pos().matches("\\d+$") ){
				int mathObjIndex = Integer.valueOf(pairs.get(index - 1).pos());
				StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

				//combine entities, like in case of "of"
				switch(anchor){
				case "of":					
					Pair nextPair = pairs.get(index + 1);
					if(nextPair.pos().matches("\\d+$")){
						pairs.get(index).set_pos(nextPair.pos());
						Struct prevStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));	
						tempStruct.add_previous(prevStruct, "of"); 
						//set to null instead of removing, to keep indices right
						mathEntList.set(Integer.valueOf(nextPair.pos()), null);
						
					}
					break;				
				}
				
			}

		} 		
		
		//arraylist of structs
		ArrayList<Struct> structList = new ArrayList<Struct>();		

		//Iterator<StructH<HashMap<String, String>>> mathEntIter = mathEntList.iterator();
		ListIterator<Pair> pairsIter = pairs.listIterator();
		
		String prevPos = "-1";
		//use anchors (of, with) to gather terms together into entities
		while(pairsIter.hasNext() ){						
			Pair curPair = pairsIter.next();
			
			if(curPair.pos().matches("^\\d+$")){
				
				if(curPair.pos().equals(prevPos)){
					continue;
				}
				
				StructH<HashMap<String, String>> curStruct = 
						mathEntList.get(Integer.valueOf(curPair.pos()));
				
				if(curStruct != null){					
					structList.add(curStruct);
				}
				
				prevPos = curPair.pos();
				
			}else{
				//current word hasn't classified into an ent
				//////
				
				
				//combine adverbs into verbs, look 2 phrases before
				if(curPair.pos().equals("adverb")){
					int structListSize = structList.size();
					
					if(structListSize > 1 && structList.get(structListSize-2).type().equals("verb")){
						StructA<?, ?> verbStruct = (StructA<?, ?>)structList.get(structListSize-2);
						//verbStruct should not have prev2, also prev2 type should be String
						verbStruct.set_prev2(curPair.word());
						continue;
					}
					else if(structListSize > 0 && structList.get(structListSize-1).type().equals("verb")){
						StructA<?, ?> verbStruct = (StructA<?, ?>)structList.get(structListSize-1);
						verbStruct.set_prev2(curPair.word());						
						continue;
					}
				}
				
				//is leaf if prev2 is empty string ""
				StructA<String, String> newStruct = 
						new StructA<String, String>(curPair.word(), "", curPair.pos());				
				
				structList.add(newStruct);
				
			}
			
			//add entity to entitiesFound
			//empty ppt map, to be added to later
			
			//use last noun as entity, if bunch of different nouns in sequence
			//unless compound noun
			
			//look in hash map the longest possible entity (most descriptive)
			//like field vs field extension					
			
			//if property found, combine to make entity property
			//...try multiple entities instead of first one found
			//add to list of properties 
			//if adjective, group with nearest entity
			//after going through entities in sentence first
			//also templating, "is" is a big hint word
			//add as property					
			
			//tokens.add("");
			
		}
		
		//categorize each term		
		
		//have to look ahead
		//return arraylist of hashmaps
		return structList;
	}
	
	//or, of are names, their types are struct
	//use arraylist of hashmaps. each hashmap has name (field, or), 
	//type (entity, struct, ppt), 
	//called (F), 
	//child,  ppt (char p, alg closed, in G), 
	
	/* Takes in LinkedHashMap of entities/ppt, and connectives
	 * parse using structMap, get big structures such as "is", "has"
	 * Chart parser.
	 */
	//@SuppressWarnings("unchecked")
	@SuppressWarnings("unchecked")
	public void parse(ArrayList<Struct> inputList ){
		int len = inputList.size();
		
		ArrayList<ArrayList<Struct>> mx = 
				new ArrayList<ArrayList<Struct>>(len);	
		
		for(int l = 0; l < len; l++){
				mx.add(new ArrayList<Struct>(len));
				for(int i = 0; i < len; i++){
					mx.get(l).add(null);
				}
		}
		
		for(int j = 0; j < len; j++){
			
			//fill in diagonal elements
			mx.get(j).set(j, inputList.get(j));
			
			for(int i = j - 1; i >= 0; i--){
				for(int k = j-1; k >= i; k--){
					// i,k, and k+1,j
					
					Struct struct1 = mx.get(i).get(k);
					Struct struct2 = mx.get(k+1).get(j);
					
					if(struct1 == null || struct2 == null){
						continue;
					}					
					
					//look ahead 2 places for "is," "has" or general verb 
					//precedence rules for or/and etc
					/* 
					if(struct1.type().matches("or|and") ){						
						for(int l = 1; l < 3; l++){
							if(j+l < len && inputList.get(j+l).type().matches("is|has") ){
								continue;
							}
						}
					}  */
					
					//combine/reduce types, like or_ppt, for_ent, in_ent
					String type1 = struct1.type();
					String type2 = struct2.type();
					//look up combined in struct table, like or_ent
					//get value as name for new hash table, table with prev field
					//new type? entity, with extra ppt
					//name: or. combined ex: or_adj (returns ent), or_ent (ent)
					String combined = type1 + "_" + type2;
					
					if(structMap.containsKey(combined)){
						String newType = structMap.get(combined);
						
						//in case A_or_A set the mx element right below to null
						//to set precedence, so A won't be grouped to others later
						if(i > 0 && (type1.equals("or") || type2.equals(mx.get(i-1).get(i-1)))){
							mx.get(i+1).set(j, null);
						}
						
						//newChild means to fuse second entity into first one
						
						if(newType.equals("newChild")){
							//struct1 should be of type StructH to receive a child
							//assert ensures rule correctness
							assert struct1 instanceof StructH;
							((StructH<?>)struct1).add_previous(struct2, type1);
							
							mx.get(i).set(j, struct1);

						}else{
							//symbol only occurs in StructA /////ugly downcast
							//if symbol and a given name to some entity							
							if(type1.equals("symb") ){
								String entKey = ((StructA<String,?>)struct1).prev1();
								if(namesMap.containsKey(entKey)){
									StructH<HashMap<String, String>> entity = namesMap.get(entKey);
			
									//modify type of struct1 and add struct to mx
									struct1.set_type(entity.struct().get("type"));
								}
							}
							
							//create new StructA and put in mx
							StructA<Struct, Struct> parentStruct = 
									new StructA<Struct, Struct>(struct1, struct2, newType);
								
							mx.get(i).set(j, parentStruct);
							
						}
						
						break;
					}
					
				}
				
			}			
		}
		
		//print out the system
		dfs(mx.get(0).get(len-1));		
		System.out.println();
		
	}
	
	@SuppressWarnings("unchecked") //don't do this!!
	public void dfs(Struct struct){
		//don't like instanceof here
		if(struct instanceof StructA){
			
			System.out.print(struct.type());
			
			System.out.print("[");
			//don't know type at compile time			
			if(((StructA<?, ?>)struct).prev1() instanceof Struct){
				//((StructA<Struct, ?>)struct).prev1().type();
				if(!((StructA<Struct, ?>)struct).prev1().type().equals(struct.type())){
					dfs(((StructA<Struct, ?>)struct).prev1());
					System.out.print(", ");
				}
			}			
						
			if(((StructA<?, ?>)struct).prev2() instanceof Struct){
				//avoid printing is[is], ie case when parent has same type as child
				if(!((StructA<?, Struct>)struct).prev2().type().equals(struct.type()))
					dfs(((StructA<?, Struct>)struct).prev2());
			}			
			
			if(((StructA<?, ?>)struct).prev1() instanceof String){
				System.out.print(((StructA<?, ?>)struct).prev1());
			}
			if(((StructA<?, ?>)struct).prev2() instanceof String){
				if(!((StructA<?, ?>)struct).prev2().equals(""))
					System.out.print(", ");
				System.out.print(((StructA<?, ?>)struct).prev2());
			} 
			
			System.out.print("]");
		}else if(struct instanceof StructH){
			
			System.out.print(struct.toString());
			
			ArrayList<Struct> prev = ((StructH<?>)struct).previous();
			if(prev == null || prev.size() == 0)
				return;
			
			System.out.print("[");
			for(int i = 0; i < prev.size(); i++){
				dfs(prev.get(i));
			}
			System.out.print("]");
		}
	}	
	
	/*
	 * Preprocess. Remove fluff words. The, a, 
	 * Remove endings, -ing, -s
	 */
	public String[] preprocess(String[] str){
		ArrayList<String> wordList = new ArrayList<String>();
		for(int i = 0; i < str.length; i++){
			if(!fluffMap.containsKey(str[i])){
				wordList.add(str[i]);
			}
		}		
		return wordList.toArray(new String[0]);
	}
	
	//the char of F_p is p
	public static void main(String[] args){
		ThmP1.buildMap();
		
		ThmP1 p1 = new ThmP1();
		//String[] strAr = p1.preprocess("a disjoint or perfect field is a field".split(" "));
		//String[] strAr = p1.preprocess("quadratic extension has degree 2".split(" "));
		//String[] strAr = p1.preprocess("finite field is field".split(" "));
		//String[] strAr = p1.preprocess("field F extend field F".split(" "));
		
		//String[] strAr = p1.preprocess("a field or ring is a ring".split(" "));
		String[] strAr = p1.preprocess("let T be any linear transformation ".split(" "));
		
		//String[] strAr = p1.preprocess("if field is ring then ring is ring".split(" "));
		
		p1.parse(p1.tokenize(strAr));
		//p1.parse(p1.tokenize(p1.preprocess("characteristic of Fp is p".split(" "))));
		
		
	}
	
}
