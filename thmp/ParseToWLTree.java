package thmp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.WLCommand.PosTerm;
import thmp.WLCommand.WLCommandComponent;

/**
 * Parses to WL Tree. Using more WL-like structures.
 * Uses ParseStrut as nodes.
 * 
 * @author yihed
 *
 */
public class ParseToWLTree {
	
	/**
	 * Deque used as Stack to store the Struct's that's being processed. 
	 * Pop off after all required terms in a WL command are met.
	 * Each level keeps a reference to some index of the deque.
	 */
	private static Deque<Struct> structDeque;	
	
	/**
	 * List to keep track all triggered WLCommands
	 */
	private static List<WLCommand> WLCommandList;
	
	/**
	 * Trigger word lookup
	 */
	private static final Multimap<String, WLCommand> WLCommandMap = WLCommandsList.WLCommandMap();
	
	/**
	 * Entry point for depth first search.
	 * @param struct
	 * @param parsedSB
	 * @param headParseStruct
	 * @param numSpaces
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB, ParseStruct headParseStruct, int numSpaces) {
		structDeque = new ArrayDeque<Struct>();
		WLCommandList = new ArrayList<WLCommand>();
		dfs(struct, parsedSB, headParseStruct, numSpaces, structDeque, WLCommandList);
	}
	
	/**
	 * Searches through parse tree and matches with ParseStruct's.
	 * Convert to visitor pattern!
	 * 
	 * @param struct
	 * @param parsedSB String
	 * @param headStruct the nearest ParseStruct that's collecting parses
	 * @param numSpaces is the number of spaces to print. Increment space if number is 
	 */
	public static void dfs(Struct struct, StringBuilder parsedSB, ParseStruct headParseStruct, int numSpaces,
			Deque<Struct> structDeque, List<WLCommand> WLCommandList) {
		//index used to keep track of where in Deque this stuct is
		//to pop off at correct index later
		int structDequeIndex = structDeque.size();
		
		//if trigger a WLCommand, 
		boolean isTrigger = false;
		Collection<WLCommand> triggeredCol = null;

		if (struct instanceof StructA && struct.prev1() instanceof String) {
			triggeredCol = WLCommandMap.get((String)struct.prev1());			
		}else if(struct instanceof StructH){
			//null should be a valid key for Multimap
			triggeredCol = WLCommandMap.get(struct.struct().get("name"));
		}
		
		//Need to copy the WLCommands! So not to modify the ones in WLCommandMap
		
		if(triggeredCol != null && !triggeredCol.isEmpty()){			
			//is trigger, add all commands in list 
			//WLCommand curCommand;
			for(WLCommand curCommand : triggeredCol){
				
				//backtrack until either stop words (ones that trigger ParseStructs) are reached
				//or until commands prior to triggerWordIndex are filled
				List<PosTerm> posTermList = WLCommand.posTermList(curCommand);
				int triggerWordIndex = WLCommand.triggerWordIndex(curCommand);
				//whether terms prior to trigger word are satisfied
				boolean curCommandSat = true;
				//list of structs waiting to be inserted to curCommand via addComponent
				//temporary list instead of adding directly, since the terms prior need 
				//to be added backwards (always add at beginning), and list will not be 
				//added if !curCommandSat.
				List<Struct> waitingStructList = new ArrayList<Struct>();
				//array of booleans to keep track of which deque Struct's have been used
				boolean[] usedStructsBool = new boolean[structDeque.size()];
				
				//start from the word before the trigger word
				//iterate through posTermList
				posTermListLoop: for(int i = triggerWordIndex - 1; i > -1; i--){
					WLCommandComponent curCommandComponent = posTermList.get(i).commandComponent();
					
					//int curStructDequeIndex = structDequeIndex;
					//iterate through Deque backwards
					Iterator<Struct> dequeReverseIter = structDeque.descendingIterator();
					int dequeIterCounter = structDeque.size() - 1;
					
					while(dequeReverseIter.hasNext()){
					//for each struct in deque, go through list to match
					//Need a way to tell if all filled
						Struct curStructInDeque = dequeReverseIter.next();
						//avoid repeating this: 
						String nameStr = "";
						if(curStructInDeque instanceof StructA && curStructInDeque.prev1() instanceof String){
							nameStr = (String)curStructInDeque.prev1();
						}else if(curStructInDeque instanceof StructH){
							nameStr = curStructInDeque.struct().get("name");
						}
						
						if(curStructInDeque.type().matches(curCommandComponent.posTerm())
								&& nameStr.matches(curCommandComponent.name()) 
								&& !usedStructsBool[dequeIterCounter]){
							//&& curStructInDeque.name().matches(curCommandComponent.name())
							//see if name matches, if match, move on, continue outer loop
							//need a way to mark structs already matched! 
							
							//add struct to the matching Component if found a match!							
							//add at beginning since iterating backwards
							waitingStructList.add(0, curStructInDeque);
							
							usedStructsBool[dequeIterCounter] = true;
							continue posTermListLoop;
						}
						dequeIterCounter--;
					}
					curCommandSat = false;
					//done iterating through deque, but no match found; curCommand cannot be satisfied
					break;
				}
				//curCommand's terms before trigger word are satisfied. 
				if(curCommandSat){
					for(Struct curStruct : waitingStructList){
						WLCommand.addComponent(curCommand, curStruct);
					}
					WLCommandList.add(curCommand);
				}
			}
			
			isTrigger = true;
			
		}
		//cur struct does not trigger
		else{
			//add struct to stack
			structDeque.add(struct);
			//add struct to all WLCommands in WLCommandList
			//check if satisfied
			for(WLCommand curCommand : WLCommandList){
				boolean commandSat = WLCommand.addComponent(curCommand, struct);
			}
		}
		
		// use visitor pattern!		
		if (struct instanceof StructA) {
			//create ParseStruct's
			//the type T will depend on children. The type depends on struct's type
			//figure out types now, fill in later to ParseStruct later. 
			
			//ParseStructType parseStructType = ParseStructType.getType(struct.type());
			//ListMultimap<ParseStructType, ParseStruct> subParseTree = ArrayListMultimap.create();
			//ParseStruct parseStruct;
			ParseStruct curHeadParseStruct = headParseStruct;
			/*boolean checkParseStructType0 = checkParseStructType(parseStructType);
			if(checkParseStructType0){
				curHeadParseStruct = new ParseStruct(parseStructType, "", struct);
				headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
				//set to "" so to not print duplicates
				//struct.set_prev1("");
				
				numSpaces++;
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.print("\n " + space + struct.type() + ":>");
				parsedSB.append("\n" + space);	
			}		*/
			
			/*
			if(struct.type().matches("hyp|let") ){
				//create new ParseStruct
				//ParseStructType parseStructType = ParseStructType.getType(struct.type());
				ParseStruct newParseStruct = new ParseStruct(parseStructType, "", struct);
				headParseStruct.addToSubtree(parseStructType, newParseStruct);
				
				numSpaces++;
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
				parsedSB.append("\n" + space);				
			} */
			
			System.out.print(struct.type());
			parsedSB.append(struct.type());
			
			System.out.print("[");
			parsedSB.append("[");
			
			// don't know type at compile time
			if (struct.prev1() instanceof Struct) {
				//ParseStruct curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				String prev1Type = ((Struct)struct.prev1()).type();
				ParseStructType parseStructType = ParseStructType.getType(prev1Type);
				boolean checkParseStructType = checkParseStructType(parseStructType);
				if(checkParseStructType){
					curHeadParseStruct = new ParseStruct(parseStructType, "", (Struct)struct.prev1());
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					//set to "" so to not print duplicates
					//struct.set_prev1("");
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					//System.out.println(space);
					System.out.print("\n " + space + prev1Type + ":>");
					parsedSB.append("\n " + space + prev1Type + ":>");	
				}				
				//pass along headStruct, unless created new one here
				dfs((Struct) struct.prev1(), parsedSB, curHeadParseStruct, numSpaces);
				if(checkParseStructType){
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.println(space);
				}
			}
			
			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (struct.prev2() instanceof Struct) {
				
				System.out.print(", ");
				parsedSB.append(", ");
				
				// avoid printing is[is], ie case when parent has same type as
				// child
				String prev2Type = ((Struct)struct.prev2()).type();
				ParseStructType parseStructType = ParseStructType.getType(prev2Type);
				curHeadParseStruct = headParseStruct;
				//check if need to create new ParseStruct
				boolean checkParseStructType = checkParseStructType(parseStructType);
				if(checkParseStructType){
					curHeadParseStruct = new ParseStruct(parseStructType, "", (Struct)struct.prev2());
					headParseStruct.addToSubtree(parseStructType, curHeadParseStruct);
					//struct.set_prev2("");
					
					numSpaces++;
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.print("\n " + space + prev2Type + ":>");
					parsedSB.append("\n" + space + prev2Type + ":>");	
				}
				
				
				dfs((Struct) struct.prev2(), parsedSB, curHeadParseStruct, numSpaces);
				if(checkParseStructType){
					struct.set_prev2("");
					String space = "";
					for(int i = 0; i < numSpaces; i++) space += " ";
					System.out.println(space);
				}
			}

			if (struct.prev1() instanceof String) {
				System.out.print(struct.prev1());
				parsedSB.append(struct.prev1());
			}
			if (struct.prev2() instanceof String) {
				if (!struct.prev2().equals("")){
					System.out.print(", ");
					parsedSB.append(", ");
				}
				System.out.print(struct.prev2());
				parsedSB.append(struct.prev2());
			}

			System.out.print("]");
			parsedSB.append("]");
			
			/*if(checkParseStructType0){
				String space = "";
				for(int i = 0; i < numSpaces; i++) space += " ";
				System.out.println(space);
			}*/
			//create new parseStruct to put in tree
			//if Struct (leaf) and not ParseStruct (overall head), done with subtree and return
			
			
		} else if (struct instanceof StructH) {

			System.out.print(struct.toString());
			parsedSB.append(struct.toString());

			ArrayList<Struct> children = struct.children();
			ArrayList<String> childRelation = struct.childRelation();

			if (children == null || children.size() == 0)
				return;

			System.out.print("[");
			parsedSB.append("[");

			for (int i = 0; i < children.size(); i++) {
				System.out.print(childRelation.get(i) + " ");
				parsedSB.append(childRelation.get(i) + " ");

				dfs(children.get(i), parsedSB, headParseStruct, numSpaces);
			}
			System.out.print("]");
			parsedSB.append("]");
		}
	}
	
	/**
	 * 
	 * @param type The enum ParseStructType
	 * @return whether to create new ParseStruct to parseStructHead
	 */
	private static boolean checkParseStructType(ParseStructType type){
		boolean createNew = true;
		if(type == ParseStructType.NONE)
			createNew = false;
		return createNew;
	}
}
