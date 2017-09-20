//
//  Created by yetianfeng on 10/22/16.
//  Copyright © 2016 yetianfeng. All rights reserved.
//
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;

public class FullFirstOrder {

	public static void main(String[] args) {
		try {
			File inPutFile = new File("input.txt");
			Scanner input = new Scanner(inPutFile);
			int numberOfQueries = Integer.parseInt(input.nextLine());
			String[] queries = new String[numberOfQueries];
			readInputFile(queries, input);
			int numberOfSentences = Integer.parseInt(input.nextLine());
			String[] originalKB = new String[numberOfSentences];
			readInputFile(originalKB, input);
			input.close(); // all input read done
			File outPutFile = new File("output.txt");
			PrintWriter output = new PrintWriter(outPutFile);
			run(queries, originalKB, output);
			output.close();
			System.exit(0);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void run(String[] queries, String[] originalKB, PrintWriter output) {
		KB kb = new KB(originalKB, new Parser());
		kb.buildKB(); 
		Query query = new Query(queries, new Parser());
		LinkedList<Literal> notQueries = query.negateQueries();
		for (Literal notQuery : notQueries) {
			LinkedList<Literal> list = new LinkedList<Literal>();
			Set<String> leftSet = new HashSet<String>();
			Set<String> rightSet = new HashSet<String>();
			list.add(notQuery);
			long start = System.currentTimeMillis();
			long end = start + 240*1000; // 60 seconds * 1000 ms/sec
			if (kb.resolution(notQuery, list, leftSet, rightSet, end)) {
				//System.out.println("TRUE");
				output.println("TRUE");
			} else {
				//System.out.println("FALSE");
				output.println("FALSE");
			}
		}
	}

	/**
        Helper function for reading input
     @param String[] array, Scanner input
     @returns void: complete filling the array(fixed size)
	 */
	private static void readInputFile(String[] array, Scanner input) {
		for (int i = 0; i < array.length; i++) {
			array[i] = input.nextLine();
		}
	}
}

class Query {
	private String[] queries;
	private Parser parser;
	Query (String[] queries, Parser parser) {
		this.queries = queries;
		this.parser = parser;
	}
	LinkedList<Literal> negateQueries() {
		LinkedList<Literal> notQueries = new LinkedList<Literal>();
		for (String query : this.queries) {
			String replaceAllSpaces = query.replaceAll(" ", "");
			char[] charArray = replaceAllSpaces.toCharArray(); // convert string to charArray
			int[] start = {0};
			Sentence tmp = this.parser.CNFParser(start, charArray.length, charArray);
			Sentence tmp1 = remoDoubleNegate(tmp);
			if (Not.class.isInstance(tmp1)) {
				notQueries.add((Literal)tmp1.getChildren().get(0));
			} else {
				((Literal)tmp1).setValue(false); 
				notQueries.add((Literal)tmp1);
			}
		}
		return notQueries;
	}
	// Removing double negations: case like ~~Q, ~~~Q, ~~~~Q
	private static Sentence remoDoubleNegate(Sentence s) {
		if (Not.class.isInstance(s) && Not.class.isInstance(s.getChildren().get(0))) {
			return remoDoubleNegate(s.getChildren().get(0).getChildren().get(0));
		} else {
			return s;
		}
	}
}

class KB implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String[] originalKB;
	private Parser parser;
	private ArrayList<Sentence> sentences;
	private ArrayList<ArrayList<Literal>> KB;
	private Map<String, ArrayList<Literal>> predicateMap; 
	private Map<String, ArrayList<Literal>> notPredicateMap; 

	KB (String[] originalKB, Parser parser) {
		this.originalKB = originalKB;
		this.parser = parser;
		this.sentences = new ArrayList<Sentence>();
		this.KB = new ArrayList<ArrayList<Literal>>();
		this.predicateMap = new HashMap<String, ArrayList<Literal>>(); 
		this.notPredicateMap = new HashMap<String, ArrayList<Literal>>(); 
	}


	@SuppressWarnings("unchecked")
	static ArrayList<Literal> deepClone(ArrayList<Literal> line) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(line);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (ArrayList<Literal>)ois.readObject();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	void buildKB() {
		Map<String, PriorityQueue<Literal>> preMap = new HashMap<String, PriorityQueue<Literal>>();  
		Map<String, PriorityQueue<Literal>> notPreMap = new HashMap<String, PriorityQueue<Literal>>(); 
		for (String origSentence : this.originalKB) {
			String replaceAllSpaces = origSentence.replaceAll(" ", "");
			char[] charArray = replaceAllSpaces.toCharArray(); // convert string to charArray
			int[] start = {0};
			this.sentences.add(this.parser.CNFParser(start, charArray.length, charArray));
		}
		for (Sentence complexSentence : this.sentences) {
			createSeparateClauses(complexSentence.toCNF(), preMap, notPreMap);
		}
		convertMap(preMap, notPreMap);
	}

	ArrayList<ArrayList<Literal>> getKB() {
		return this.KB;
	}
	int getKBSize() {
		return this.KB.size();
	}
	Map<String, ArrayList<Literal>> getPredicateMap() {
		return this.predicateMap;
	}

	Map<String, ArrayList<Literal>> getNotPredicateMap() {
		return this.notPredicateMap;
	}
	/**
    create separate clauses, this is the final step to finish KB's establishment
 @param Sentence sentence, LinkedList<Sentence> KB
 @returns void: complete separation, add clauses only contain Not or Or into KB list
	 */
	private void createSeparateClauses(Sentence sentence, Map<String, PriorityQueue<Literal>> preMap, Map<String, PriorityQueue<Literal>> notPreMap) {
		if (And.class.isInstance(sentence)) {
			Sentence leftsubsentence = sentence.getChildren().get(0);
			createSeparateClauses(leftsubsentence, preMap, notPreMap);
			Sentence rightsubsentence = sentence.getChildren().get(1);
			createSeparateClauses(rightsubsentence, preMap, notPreMap);
		} else {
			ArrayList<Literal> literals = new ArrayList<Literal>();
			createSeparateLiterals(sentence, literals, preMap, notPreMap);
			this.KB.add(literals);
		}
	}
	//F(x,y) => F(y,x)
	//F(x,y) & F(y,z) => F(x,z)
	//	F(A,B)
	//	F(B,C)
	//	F(C,D)
	private void createSeparateLiterals(Sentence sentence, ArrayList<Literal> literals, Map<String, PriorityQueue<Literal>> preMap, Map<String, PriorityQueue<Literal>> notPreMap) {
		if (Or.class.isInstance(sentence)) {
			Sentence leftsubsentence = sentence.getChildren().get(0);
			createSeparateLiterals(leftsubsentence, literals, preMap, notPreMap);
			Sentence rightsubsentence = sentence.getChildren().get(1);
			createSeparateLiterals(rightsubsentence, literals, preMap, notPreMap);
		} else if (Not.class.isInstance(sentence)) {
			Literal tmp = (Literal)sentence.getChildren().get(0);
			tmp.setValue(false);
			tmp.getElement().setRow(KB.size());
			tmp.getElement().setColumn(literals.size());
			if (Predicate.class.isInstance(tmp.getElement())) {
				for (Element e : ((Predicate)tmp.getElement()).getArguments()) {
					if (Variable.class.isInstance(e)) {
						((Variable)e).initializeCode(KB.size());
					}
				}
			}
			literals.add(tmp);
			createMap(tmp, preMap, notPreMap);
		} else {
			Literal tmp = (Literal)sentence;
			tmp.getElement().setRow(KB.size());
			tmp.getElement().setColumn(literals.size());
			if (Predicate.class.isInstance(tmp.getElement())) {
				for (Element e : ((Predicate)tmp.getElement()).getArguments()) {
					if (Variable.class.isInstance(e)) {
						((Variable)e).initializeCode(KB.size());
					}
				}
			}
			literals.add(tmp);
			createMap(tmp, preMap, notPreMap);
		}
	}

	private void createMap(Literal l, Map<String, PriorityQueue<Literal>> preMap, Map<String, PriorityQueue<Literal>> notPreMap) {
		if (l.value()) {
			String name = l.getElement().getName();
			if (preMap.containsKey(name)) {
				preMap.get(name).offer(l);
			} else {
				PriorityQueue<Literal> pq = new PriorityQueue<Literal>(11, new Comparator<Literal>(){
					public int compare(Literal i, Literal j) {
						if (Predicate.class.isInstance(i.getElement())) {
							if (((Predicate)i.getElement()).getNumberOfVariableArguments() > ((Predicate)j.getElement()).getNumberOfVariableArguments()) {
								return -1;
							} else if (((Predicate)i.getElement()).getNumberOfVariableArguments() < ((Predicate)j.getElement()).getNumberOfVariableArguments()) {
								return 1;
							} else {
								return 0;
							}
						}
						else return 0;
					}
				});
				pq.offer(l);
				preMap.put(name, pq);
			}
		} else {
			String name = l.getElement().getName();
			if (notPreMap.containsKey(name)) {
				notPreMap.get(name).offer(l);
			} else {
				PriorityQueue<Literal> pq = new PriorityQueue<Literal>(11, new Comparator<Literal>(){
					public int compare(Literal i, Literal j) {
						if (Predicate.class.isInstance(i.getElement())) {
							if (((Predicate)i.getElement()).getNumberOfVariableArguments() > ((Predicate)j.getElement()).getNumberOfVariableArguments()) {
								return -1;
							} else if (((Predicate)i.getElement()).getNumberOfVariableArguments() < ((Predicate)j.getElement()).getNumberOfVariableArguments()) {
								return 1;
							} else {
								return 0;
							}
						}
						else return 0;
					}
				});
				pq.offer(l);
				notPreMap.put(name, pq);
			}
		}
	}
	private void convertMap(Map<String, PriorityQueue<Literal>> preMap, Map<String, PriorityQueue<Literal>> notPreMap) {
		Set<Map.Entry<String, PriorityQueue<Literal>>> preMapEntrySet = preMap.entrySet();
		Set<Map.Entry<String, PriorityQueue<Literal>>> notPreMapEntrySet = notPreMap.entrySet();
		for (Map.Entry<String, PriorityQueue<Literal>> entry : preMapEntrySet) {
			this.predicateMap.put(entry.getKey(), new ArrayList<>(entry.getValue())); 
		}
		for (Map.Entry<String, PriorityQueue<Literal>> entry : notPreMapEntrySet) {
			this.notPredicateMap.put(entry.getKey(), new ArrayList<>(entry.getValue())); 
		}
	}

	boolean resolution(Literal query, LinkedList<Literal> literals, Set<String> leftSet, Set<String> rightSet, long end) {
		if (literals.isEmpty()) {
			return true;
		}
		if (System.currentTimeMillis() > end) {
			return false;
		}
		if (literals.size() == 1) {
			Literal q = Literal.deepClone(query);
			if (q.value()) {
				q.setValue(false);
			} else {
				q.setValue(true);
			}
			literals.add(q);
			if (factoring(literals).size() == 1) {
				return true;
			}
			else {
				literals.removeLast();
			}
		}
		if (literals.getFirst().value()) {
			Set<Map.Entry<String, ArrayList<Literal>>> entrySet = this.notPredicateMap.entrySet();
			for (Map.Entry<String, ArrayList<Literal>> entry : entrySet) {
				if (entry.getKey().equals(literals.getFirst().getElement().getName())) {
					ArrayList<Literal> matchedPredicate = entry.getValue();
					for (int i = 0; i < matchedPredicate.size(); i++) {
						int row = matchedPredicate.get(i).getElement().getRow();
						int column = matchedPredicate.get(i).getElement().getColumn();
						List<Literal> matchedSentence = new ArrayList<Literal>(deepClone(this.KB.get(row)));
						LinkedList<Literal> ll = (LinkedList<Literal>) Literal.deepClone(literals);
						if (!unification(literals.getFirst(), matchedSentence.get(column), literals, matchedSentence)) {
							literals = ll;
							continue; 
						}
						//System.out.println("Query list after unitfication" + literals);
						//System.out.println("MatchedSentence after unitfication" + matchedSentence);
						literals.removeFirst();
						matchedSentence.remove(column);
						literals.addAll(0, matchedSentence);
						literals = (LinkedList<Literal>) factoring(literals);
						//System.out.println("Query list after added" + literals);
						if (!leftSet.add(literals.toString())||!rightSet.add(literals.toString())) {
							literals = ll;
							continue;
						}
						if (resolution(query, literals, leftSet, rightSet, end)) {
							return true;
						} else {
							literals = ll;
							//System.out.println("Query list after removed" + literals);
						}
					}
					break;
				}
			}
		} else {
			Set<Map.Entry<String, ArrayList<Literal>>> entrySet = this.predicateMap.entrySet();
			for (Map.Entry<String, ArrayList<Literal>> entry : entrySet) {
				if (entry.getKey().equals(literals.getFirst().getElement().getName())) {
					ArrayList<Literal> matchedPredicate = entry.getValue();
					for (int i = 0; i < matchedPredicate.size(); i++) {
						int row = matchedPredicate.get(i).getElement().getRow();
						int column = matchedPredicate.get(i).getElement().getColumn();
						List<Literal> matchedSentence = new ArrayList<Literal>(deepClone(this.KB.get(row)));
						LinkedList<Literal> ll = (LinkedList<Literal>) Literal.deepClone(literals);
						if (!unification(literals.getFirst(), matchedSentence.get(column), literals, matchedSentence)) {
							literals = ll;
							continue; 
						}
						//System.out.println("Query list after unitfication" + literals);
						//System.out.println("MatchedSentence after unitfication" + matchedSentence);
						literals.removeFirst();
						matchedSentence.remove(column);
						literals.addAll(0, matchedSentence);
						literals = (LinkedList<Literal>) factoring(literals);
						//System.out.println("Query list after added" + literals);
						if (!leftSet.add(literals.toString())||!rightSet.add(literals.toString())) {							
							literals = ll;
							continue;
						}
						if (resolution(query, literals, leftSet, rightSet, end)) {
							return true;
						} else {
							literals = ll;
							//System.out.println("Query list after removed" + literals);
						}						
					}
					break;
				}
			}
		}
		return false;
	}


	private List<Literal> factoring(List<Literal> literals) {
		for (int i = 0; i < literals.size(); i++) {
			for (int j = i + 1; j < literals.size(); j++) {
				if (literals.get(i).getElement().getName().equals(literals.get(j).getElement().getName()) && literals.get(i).value() == literals.get(j).value()) {
					List<Literal> ll = Literal.deepClone(literals);
					if (unification(literals.get(i), literals.get(j), literals)) {
						literals.remove(j--);
					} else {
						literals = ll;
					}
				}
			}
		}
		return literals;
	}
	private boolean unification(Literal left, Literal right, List<Literal> literals) {
		if (Predicate.class.isInstance(left.getElement()) && Predicate.class.isInstance(right.getElement())) { 
			Element[] argumentsOfLeft = ((Predicate)left.getElement()).getArguments();
			Element[] argumentsOfRight = ((Predicate)right.getElement()).getArguments();
			Element[] argumentsOfLeftCopy = Element.deepClone(argumentsOfLeft);
			Element[] argumentsOfRightCopy = Element.deepClone(argumentsOfRight);
			for (int i = 0; i < argumentsOfLeft.length; i++) {
				if (Variable.class.isInstance(argumentsOfLeft[i]) && Constant.class.isInstance(argumentsOfRight[i])) {
					int code = ((Variable)argumentsOfLeft[i]).getCode();
					((Predicate)left.getElement()).setArgument(argumentsOfRight[i], i);//
					for (Literal l : literals) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {							
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfRight[i], j);
							}
						}
					}
				} else if (Variable.class.isInstance(argumentsOfRight[i]) && Constant.class.isInstance(argumentsOfLeft[i])) {
					int code = ((Variable)argumentsOfRight[i]).getCode();
					((Predicate)right.getElement()).setArgument(argumentsOfLeft[i], i);
					for (Literal l : literals) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfLeft[i], j);
							}
						}
					}
				} else if ((Constant.class.isInstance(argumentsOfRight[i]) && Constant.class.isInstance(argumentsOfLeft[i]))) {
					if (!argumentsOfRight[i].getName().equals(argumentsOfLeft[i].getName())) {
						((Predicate)left.getElement()).setArguments(argumentsOfLeftCopy);
						((Predicate)right.getElement()).setArguments(argumentsOfRightCopy);
						return false;
					}
				} else {
					int code = ((Variable)argumentsOfLeft[i]).getCode();
					((Predicate)left.getElement()).setArgument(argumentsOfRight[i], i);//
					for (Literal l : literals) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {						
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfRight[i], j);
							}
						}
					}

				}
			}
		}
		return true;
	}

	private boolean unification(Literal left, Literal right, List<Literal> literals, List<Literal> matchedSentence) {
		if (Predicate.class.isInstance(left.getElement()) && Predicate.class.isInstance(right.getElement())) { 
			Element[] argumentsOfLeft = ((Predicate)left.getElement()).getArguments();
			Element[] argumentsOfRight = ((Predicate)right.getElement()).getArguments();
			Element[] argumentsOfLeftCopy = Element.deepClone(argumentsOfLeft);
			Element[] argumentsOfRightCopy = Element.deepClone(argumentsOfRight);
			for (int i = 0; i < argumentsOfLeft.length; i++) {
				if (Variable.class.isInstance(argumentsOfLeft[i]) && Constant.class.isInstance(argumentsOfRight[i])) {
					int code = ((Variable)argumentsOfLeft[i]).getCode();
					((Predicate)left.getElement()).setArgument(argumentsOfRight[i], i);//
					for (Literal l : literals) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfRight[i], j);
							}
						}
					}
					for (Literal l : matchedSentence) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {							
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfRight[i], j);
							}
						}
					}
				} else if (Variable.class.isInstance(argumentsOfRight[i]) && Constant.class.isInstance(argumentsOfLeft[i])) {
					int code = ((Variable)argumentsOfRight[i]).getCode();
					((Predicate)right.getElement()).setArgument(argumentsOfLeft[i], i);//
					for (Literal l : matchedSentence) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfLeft[i], j);
							}
						}
					}
					for (Literal l : literals) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfLeft[i], j);
							}
						}
					}
				} else if ((Constant.class.isInstance(argumentsOfRight[i]) && Constant.class.isInstance(argumentsOfLeft[i]))) {
					if (!argumentsOfRight[i].getName().equals(argumentsOfLeft[i].getName())) {
						((Predicate)left.getElement()).setArguments(argumentsOfLeftCopy);
						((Predicate)right.getElement()).setArguments(argumentsOfRightCopy);
						return false;
					}
				} else {//P(x,y) | P(x,x) 
					int code = ((Variable)argumentsOfLeft[i]).getCode();
					((Predicate)left.getElement()).setArgument(argumentsOfRight[i], i);
					for (Literal l : literals) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {
								((Predicate)l.getElement()).setArgument(argumentsOfRight[i], j);
							}
						}
					}
					for (Literal l : matchedSentence) {
						Element[] e = ((Predicate)l.getElement()).getArguments();
						for (int j = 0; j < e.length; j++) {							
							if (Variable.class.isInstance(e[j]) && ((Variable)e[j]).getCode() == code) {					
								((Predicate)l.getElement()).setArgument(argumentsOfRight[i], j);
							}
						}
					}
				}
			}
		}
		return true;
	}

}

class Parser {
	private final static char not = '~';
	private final static char and = '&';
	private final static char or = '|';
	private final static char imply = '=';
	private final static char openParenthesis = '(';
	private final static char closeParenthesis = ')';
	private final static char comma = ',';
	/**
        Separate each sentence of original KB into symbols(~ | & =>) and atomic sentence,
        recursively call Parser when encountering parenthesis,
        but the order for symbol and atomic sentence should be exactly matched.
     @param int[] start, int end, char[] charArray
        pass the start and end index of charArray that need to be parser
     @returns
        Sentence: complete separation and combination, return the sentence that can be applied to convert to CNF
	 */
	Sentence CNFParser(int[] start, int end, char[] charArray) {
		LinkedList<String> symbols = new LinkedList<String>();
		LinkedList<Sentence> sentences = new LinkedList<Sentence>();
		Set<Variable> variableSet = new HashSet<Variable>();
		boolean predicate = false; // flag
		for (int i = start[0]; i < end; i++) {
			if (charArray[i] == not) { // case: not
				symbols.add("~");
			} else if (charArray[i] == and) { // case: and
				symbols.add("&");
			} else if (charArray[i] == or) { // case: or
				symbols.add("|");
			} else if (charArray[i] == imply) { // case: imply, all above are symbols that should be added into symbols
				symbols.add("=");
				i++;
			} else if (charArray[i] == openParenthesis) { // case: highest priority, recursively call Parser
				int count = 1;
				for (int j = i + 1; j < end; j++) {
					if (charArray[j] == openParenthesis) {
						count++;
					} else if (charArray[j] == closeParenthesis) {
						count--;
						if (count == 0) {
							start[0] = i + 1;
							sentences.add(CNFParser(start, j, charArray));
							i = start[0] + 1;
							break;
						}
					}
				}
			} else if (Character.isLowerCase(charArray[i])) { // variable: only single lower case letter
				sentences.add(new Literal(new Variable(String.valueOf(charArray[i]))));
			} else { // constant or predicate
				StringBuilder sb = new StringBuilder();
				//String name = String.valueOf(charArray[i]);
				sb.append(charArray[i]);
				if (i + 1 == end) { // boundary case
					sentences.add(new Literal(new Constant(sb.toString())));
				}
				for (int j = i + 1; j < end; j++) {
					if (charArray[j] == openParenthesis) { // predicate: set flag
						i = j;
						predicate = true;
						break;
					}
					// ~ | & == => ( ) predicates
					// constant and should be added to atomic sentences
					if (charArray[j] == not || charArray[j] == and || charArray[j] == or || charArray[j] == imply || charArray[j] == closeParenthesis) {
						i = j - 1; // -1：go back to symbol check
						sentences.add(new Literal(new Constant(sb.toString())));
						break;
					}
					if ((j + 1) >= end) { // boundary case
						i = j;
						sb.append(charArray[j]);
						sentences.add(new Literal(new Constant(sb.toString())));
						break;
					}
					sb.append(charArray[j]);
				}
				if (predicate) { // process the arguments of predication
					Element[] arguments = new Element[120]; // at most 100
					int numberOfArguments = 0;
					int numberOfVariableArguments = 0;
					for (int k = i; k < end && charArray[k] != closeParenthesis; k++) {
						k++; // in order to pass all comma
						if (Character.isLowerCase(charArray[k])) { // argument of variable type
							boolean exit = false;
							for (Variable v:variableSet) {
								if (v.getName().equals(String.valueOf(charArray[k]))) {
									exit = true;
									arguments[numberOfArguments++] = v;
									break;
								}
							}
							if (!exit) {
								Variable v = new Variable(String.valueOf(charArray[k]));
								arguments[numberOfArguments++] = v;
								variableSet.add(v);
							}
							numberOfVariableArguments++;
						} else { // argument of constant type
							StringBuilder sbOfArgument = new StringBuilder();
							for (int l = k; l < end; l++) {
								if (charArray[l] == comma || charArray[l] == closeParenthesis) {
									k = l - 1; // already at comma, so need to go back
									arguments[numberOfArguments++] = new Constant(sbOfArgument.toString());
									break;
								}
								sbOfArgument.append(charArray[l]);
							}
						}
						i = k + 1; // pass close parenthesis
					}
					sentences.add(new Literal(new Predicate(sb.toString(), numberOfVariableArguments, Arrays.copyOf(arguments, numberOfArguments))));
				}
			}
			start[0] = i;
		}
		CNFLexer(symbols, sentences);
		return sentences.get(0);
	}
	/**
    combine the atomic sentences with corresponding symbols into complex sentence
 @param LinkedList<String> symbols, LinkedList<Sentence> sentences
 @returns void: complete combination, update the Linkedlist of sentences
	 */
	private static void CNFLexer(LinkedList<String> symbols, LinkedList<Sentence> sentences) {
		for (int i = 0; i < symbols.size(); i++) {
			if (symbols.get(i).equals("~")) {
				Sentence tmp = sentences.get(i).not();
				symbols.remove(i);
				sentences.remove(i);
				sentences.add(i, tmp);
				i--;
			}
		}
		for (int i = 0; i < symbols.size(); i++) {
			if (symbols.get(i).equals("&")) {
				Sentence tmp = sentences.get(i).and(sentences.get(i + 1));
				helpRemove(i, symbols, sentences);
				sentences.add(i, tmp);
				i--;
			}
		}
		for (int i = 0; i < symbols.size(); i++) {
			if (symbols.get(i).equals("|")) {
				Sentence tmp = sentences.get(i).or(sentences.get(i + 1));
				helpRemove(i, symbols, sentences);
				sentences.add(i, tmp);
				i--;
			}
		}
		for (int i = 0; i < symbols.size(); i++) {
			if (symbols.get(i).equals("=")) {
				Sentence tmp = sentences.get(i).implies(sentences.get(i + 1));
				helpRemove(i, symbols, sentences);
				sentences.add(i, tmp);
				i--;
			}
		}
	}
	// helper function
	private static void helpRemove(int i, LinkedList<String> symbols, LinkedList<Sentence> sentences) {
		symbols.remove(i);
		sentences.remove(i);
		sentences.remove(i);
	}
}

abstract class Sentence implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Sentence parent;
	private LinkedList<Sentence> children;

	Sentence() {
		this.parent = null;
		this.children = new LinkedList<Sentence>();
	}

	Sentence(Sentence sentence) {
		this.children = sentence.children;
		this.parent = sentence.parent;
	}
	abstract boolean value();

	static Sentence deepClone(Sentence line) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(line);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (Sentence)ois.readObject();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	String getTreeRepresentation(int level) {
		String indent = "";
		for (int i = 0; i < level; i++) {
			indent = indent + "\t";
		}
		String treeRepresentation = indent + this.toString() + "\n";
		for (int i = 0; i < this.children.size(); i++) {
			treeRepresentation += this.children.get(i).getTreeRepresentation(level + 1);
		}
		return treeRepresentation;
	}

	Sentence getParent() {
		return parent;
	}

	void setParent(Sentence parent) {
		this.parent = parent;
	}

	LinkedList<Sentence> getChildren() {
		return children;
	}

	void setChildren(LinkedList<Sentence> children) {
		this.children = children;
	}

	void addChild(Sentence sentence) {
		this.children.add(sentence);
		sentence.setParent(this);

	}
	void removeChild(Sentence sentence) {
		if (this.children.contains(sentence)) {
			this.children.remove(sentence);
		}
	}

	Not not() {
		return new Not(this);
	}
	And and(Sentence rightSentence) {
		return new And(this, rightSentence);
	}
	Or or(Sentence rightSentence) {
		return new Or(this, rightSentence);
	}
	Implication implies(Sentence sentence) {
		return new Implication(this, sentence);
	}

	Sentence toCNF() {
		Sentence CNF = eliminateImplication(this);
		CNF = applyDeMorganLaws(CNF);
		do {
			CNF = distributeOrsOverAnd(CNF);
		} while (!CNF.getTreeRepresentation(0).equals(distributeOrsOverAnd(CNF).getTreeRepresentation(0)));
		return CNF;
	}

	/**
     Use definition to eliminate all =>
     A => B = (~A) V B
     @param Sentence sentence
     @returns void: complete eliminate => and <=>
	 */
	private static Sentence eliminateImplication(Sentence sentence) {
		Sentence res = sentence;
		if (Implication.class.isInstance(sentence)) {
			res.setParent(sentence.getParent());
			res = eliminateImplication(sentence.getChildren().get(0).not().or(sentence.getChildren().get(1)));
		}
		if (!Literal.class.isInstance(sentence)) {
			for (int i = 0; i < sentence.getChildren().size(); i++) {
				Sentence newChild = eliminateImplication(sentence.getChildren().get(i));
				sentence.getChildren().remove(i);
				sentence.getChildren().add(i, newChild);
			}
		}
		return res;
	}

	/**
     Apply DeMorgan's Laws, including removing double negations
     ~(A V B) = ~(A) ^ ~(B)
     ~(A ^ B) = ~(A) V ~(B)
     ~~A = A
     ~~~A = ~A
     @param sentence
     @returns void
	 */
	private static Sentence applyDeMorganLaws(Sentence sentence) {
		Sentence res = sentence;
		if (Not.class.isInstance(sentence)) {
			Sentence subsentence = sentence.getChildren().get(0);
			if (!Not.class.isInstance(subsentence)) {
				if (Or.class.isInstance(subsentence)) {
					Sentence left = subsentence.getChildren().get(0);
					Sentence right = subsentence.getChildren().get(1);
					res.setParent(sentence);
					if (!Not.class.isInstance(left) && !Not.class.isInstance(right)) {
						res = applyDeMorganLaws(left.not().and(right.not()));
					} else if (Not.class.isInstance(left) && !Not.class.isInstance(right)) {
						subsentence.removeChild(left);
						res = applyDeMorganLaws(left.getChildren().get(0).and(right.not()));
					} else if (!Not.class.isInstance(left) && Not.class.isInstance(right)) {
						subsentence.removeChild(right);
						res = applyDeMorganLaws(left.not().and(right.getChildren().get(0)));
					} else {
						subsentence.removeChild(left);
						subsentence.removeChild(right);
						res = applyDeMorganLaws(left.getChildren().get(0).and(right.getChildren().get(0)));
					}

				} else if (And.class.isInstance(subsentence)) {
					Sentence left = subsentence.getChildren().get(0);
					Sentence right = subsentence.getChildren().get(1);
					res.setParent(sentence);
					if (!Not.class.isInstance(left) && !Not.class.isInstance(right)) {
						res = applyDeMorganLaws(left.not().or(right.not()));
					} else if (Not.class.isInstance(left) && !Not.class.isInstance(right)) {
						subsentence.removeChild(left);
						res = applyDeMorganLaws(left.getChildren().get(0).or(right.not()));
					} else if (!Not.class.isInstance(left) && Not.class.isInstance(right)) {
						subsentence.removeChild(right);
						res = applyDeMorganLaws(left.not().or(right.getChildren().get(0)));
					} else {
						subsentence.removeChild(left);
						subsentence.removeChild(right);
						res = applyDeMorganLaws(left.getChildren().get(0).or(right.getChildren().get(0)));
					}
				}
			}
			else {
				sentence.removeChild(subsentence);
				res = applyDeMorganLaws(subsentence.getChildren().get(0));
			}
		}
		if (!Literal.class.isInstance(sentence)) {
			for (int i = 0; i < sentence.getChildren().size(); i++) {
				Sentence newChild = applyDeMorganLaws(sentence.getChildren().get(i));
				sentence.getChildren().remove(i);
				sentence.getChildren().add(i, newChild);
			}
		}
		return res;
	}
	//(~P(x) | ((~P(y) | F(x,Y)) & (Q(x,g) & ~P(g))))
	//F=>X=>A
	//~F=>X=>A
	//(F|X)&(A|B)
	//(F&X)|(A&B) = (F|A) & (F|B) & (X|A) & (X|B)
	//F&X|A&B = (F|A) & (F|B) & (X|A) & (X|B)
	//F|X & A|B = (B|F|X) & (B|F|A)
	/**
     Apply distribution rules,
     distribute ors over and to split sentences that contain AND into several sentences.
     A V (B ^ C) = (A V B) ^ (A V C)
     @param sentence
     @returns void: complete splitting
	 */
	private static Sentence distributeOrsOverAnd(Sentence sentence) {
		Sentence res = sentence;
		if (Or.class.isInstance(sentence)) {
			Sentence leftsubsentence = sentence.getChildren().get(0);
			Sentence rightsubsentence = sentence.getChildren().get(1);
			if (And.class.isInstance(leftsubsentence)) {
				Sentence left = leftsubsentence.getChildren().get(0);
				Sentence right = leftsubsentence.getChildren().get(1);
				res.setParent(sentence);
				res = distributeOrsOverAnd(left.or(rightsubsentence).and(right.or(rightsubsentence)));
			} else if (And.class.isInstance(rightsubsentence)) {
				Sentence left = rightsubsentence.getChildren().get(0);
				Sentence right = rightsubsentence.getChildren().get(1);
				res.setParent(sentence);
				res = distributeOrsOverAnd(leftsubsentence.or(left).and(leftsubsentence.or(right)));
			}
		}
		if (!Literal.class.isInstance(sentence)) {
			for (int i = 0; i < sentence.getChildren().size(); i++) {
				Sentence newChild = distributeOrsOverAnd(sentence.getChildren().get(i));
				sentence.getChildren().remove(i);
				sentence.getChildren().add(i, newChild);
			}
		}
		return res;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}

class Literal extends Sentence implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Element ele;
	private boolean value;

	Literal(Element ele, boolean value) {
		super();
		this.ele = ele;
		this.value = value;
	}
	Literal(Literal literal) {
		super(literal);
		this.ele = literal.ele;
		this.value = literal.value;
	}
	Literal(Element ele) {
		this(ele, true);
	}

	@Override
	void addChild(Sentence e) {
		return; 
	}

	@Override
	void removeChild(Sentence e) {
		return; 
	}

	@Override
	LinkedList<Sentence> getChildren() {
		return null;
	}

	@Override
	void setChildren(LinkedList<Sentence> children) {
		return;
	}

	@Override
	boolean value() {
		return this.value;
	}

	@SuppressWarnings("unchecked")
	static List<Literal> deepClone(List<Literal> l) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(l);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (List<Literal>)ois.readObject();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;

		}
	}

	static Literal deepClone(Literal l) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(l);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (Literal)ois.readObject();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	Element getElement() {
		return ele;
	}

	void setElement(Element ele) {
		this.ele = ele;
	}

	void setValue(boolean value) {
		this.value = value;
	}

	@Override
	public String toString() {
		if (Predicate.class.isInstance(ele)) {
			String res = "(" + variableHelp(((Predicate)this.ele).getArguments()[0]);
			for (int i = 1; i < ((Predicate)this.ele).getArguments().length; i++) {
				res = res + "," + variableHelp(((Predicate)this.ele).getArguments()[i]);
			}
			return this.ele.getName() + res + ") / " + this.value();
		} else {
			return this.ele.getName() + " / " + this.value();
		}
	}
	private String variableHelp(Element e) {
		if (Variable.class.isInstance(e)) {
			return ((Variable)(e)).getName() + ((Variable)(e)).getCode();
		} else {
			return ((Constant)e).getName();
		}
	}
}

abstract class UnaryOperator extends Sentence {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	UnaryOperator(Sentence e) {
		super();
		this.addChild(e);
	}

	UnaryOperator(UnaryOperator uo) {
		super(uo);
	}

	Sentence getSentence() {
		return this.getChildren().get(0);
	}

}
class Not extends UnaryOperator {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Not(Sentence e) {
		super(e);
	}

	Not(Not n) {
		super(n);
	}

	@Override
	boolean value() {
		return !this.getSentence().value();
	}
}

abstract class BinaryOperator extends Sentence {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	BinaryOperator(Sentence leftSentence, Sentence rightSentence) {
		super();
		this.addChild(leftSentence);
		this.addChild(rightSentence);
	}

	BinaryOperator(BinaryOperator bo) {
		super(bo);
	}

	Sentence getRight() {
		return this.getChildren().get(1);
	}

	Sentence getLeft() {
		return this.getChildren().get(0);
	}

}

class And extends BinaryOperator {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	And(And and) {
		super(and);
	}
	And(Sentence leftSentence, Sentence rightSentence) {
		super(leftSentence, rightSentence);
	}
	@Override
	boolean value() {
		return this.getRight().value() && this.getLeft().value();
	}
}
class Or extends BinaryOperator {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Or(Or or) {
		super(or);
	}
	Or(Sentence left, Sentence right) {
		super(left, right);
	}

	@Override
	boolean value() {
		return this.getRight().value() || this.getLeft().value();
	}
}

class Implication extends BinaryOperator {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Implication(Implication i) {
		super(i);
	}
	Implication(Sentence left, Sentence right) {
		super(left, right);
	}

	@Override
	boolean value() {
		return !this.getLeft().value() || this.getRight().value();
	}
}

abstract class Element implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int row;
	private int column;
	private String name;
	Element(String name) {
		this.name = name;
		this.row = -1;
		this.column = -1;
	}

	Element(Element element) {
		this.name = element.name;
		this.row = element.row;
		this.column = element.column;
	}

	void setRow(int row) {
		this.row = row;
	}
	void setColumn(int column) {
		this.column = column;
	}

	int getRow() {
		return row;
	}
	int getColumn() {
		return column;
	}
	String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	public String toString(){
		return this.name;
	}

	static Element[] deepClone(Element[] arguments) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(arguments);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (Element[])ois.readObject();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}

class Variable extends Element {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String name;
	private int code;
	Variable(String name) {
		super(name);
		this.name = name;
	}

	Variable(Variable var) {
		super(var);
		this.name = var.name;
		this.code = var.code;
	}

	void setCode(int code) {
		this.code = code;
	}
	void initializeCode(int row) {
		this.code = (int)this.name.toCharArray()[0] + 26 * row;
	}
	int getCode() {
		return this.code;
	}
}

class Constant extends Element {
	/** Constant class 
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Constant(String name) {
		super(name);
	}
	Constant (Constant con) {
		super(con);
	}
}

class Predicate extends Element {
	/** Predicate class
	 *  must have arguments(1 to 100)
	 *  type of argument can be constant or variable
	 */ 
	private static final long serialVersionUID = 1L;
	private Element[] arguments;
	private int numberOfVariableArguments;

	Predicate(String name, int numberOfVariableArguments, Element... arguments) {
		super(name);
		this.arguments = arguments;
		this.numberOfVariableArguments = numberOfVariableArguments;
	}
	Predicate (Predicate pre) {
		super(pre);
		this.arguments = pre.arguments;
		this.numberOfVariableArguments = pre.numberOfVariableArguments;
	}

	Element[] getArguments() {
		return arguments;
	}
	Element getArgument(int index) {
		return this.arguments[index];
	}
	int getNumberOfVariableArguments() {
		return numberOfVariableArguments;
	}

	void setArguments(Element[] arguments) {
		this.arguments = arguments;
		int number = 0;
		for (Element e : arguments) {
			if (Variable.class.isInstance(e)) {
				number++;
			}
		}
		this.numberOfVariableArguments = number;
	}
	void setArgument(Element argument, int index) {
		if (Constant.class.isInstance(this.arguments[index]) && Variable.class.isInstance(argument)) {
			this.numberOfVariableArguments++;
		} else if (Variable.class.isInstance(this.arguments[index]) && Constant.class.isInstance(argument)) {
			this.numberOfVariableArguments--;
		}
		this.arguments[index] = argument;
	}

	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append(this.getName());
		sb.append("(");
		for(Element e: this.arguments){
			sb.append(e.getName());
			sb.append(",");
		}
		sb.append(")");
		return sb.toString();
	}
}

