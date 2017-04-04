package thmp.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.ServletContext;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.wolfram.jlink.*;

import thmp.TheoremContainer;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

/**
 * Theorem search. Does the computation in WL using JLink. 
 * @author yihed
 */
public class ThmSearch {

	/**
	 * Matrix of documents. Columns are documents.
	 * Rows are terms.
	 */
	private static double[][] docMx;
	private static final Logger logger = LogManager.getLogger(ThmSearch.class);
	
	//public static final String[] ARGV = new String[]{"-linkmode", "launch", "-linkname", 
	//"\"/Applications/Mathematica2.app/Contents/MacOS/MathKernel\" -mathlink"};	
	//path for on Linux VM 
	//private static final String[] ARGV;
	
	//number of nearest vectors to get for Nearest[]
	private static final int NUM_NEAREST = 3;
	//private static final int NUM_SINGULAR_VAL_TO_KEEP = 20;
	//cutoff for a correlated term to be considered
	private static final int COR_THRESHOLD = 3;
	private static final int LIST_INDEX_SHIFT = 1;
	private static final String combinedTDMatrixRangeListName = "combinedRangeList";
	private static final boolean USE_FULL_MX = false;	
	
	/**
	 * Class for finding nearest giving query.
	 */
	public static class ThmSearchQuery{
		private static final int QUERY_VEC_LENGTH;
		private static final KernelLink ml;	
		private static final String V_MX;
		private static final boolean DEBUG = false;
		
	static{		
		//use OS system variable to tell whether on VM or local machine, and set InstallDirectory 
		//path accordingly.
		/*String OS_name = System.getProperty("os.name");
		if(OS_name.equals("Mac OS X")){
			ARGV = new String[]{"-linkmode", "launch", "-linkname", 
					"\"/Applications/Mathematica2.app/Contents/MacOS/MathKernel\" -mathlink"};
		}else{
			//path on Linux VM
			//ARGV = new String[]{"-linkmode", "launch", "-linkname", 
					//"\"/usr/local/Wolfram/Mathematica/11.0/Executables/MathKernel\" -mathlink"};
			ARGV = new String[]{"-linkmode", "launch", "-linkname", "math -mathlink"};
		}*/
		
		ml = FileUtils.getKernelLinkInstance();		
		String msg = "Kernel instance acquired...";
		logger.info(msg);
		int vector_vec_length = -1;
		
		try{
			ServletContext servletContext = CollectThm.getServletContext();
			//String pathToMx = "src/thmp/data/termDocumentMatrixSVD.mx";
			/*Need to load both projection matrices, and the matrix of combined 
			  projected thm vectors */
			
			/*mx file also depends on the system!*/
			String pathToProjectionMx = getSystemProjectionMxFilePath();
			
			/*path for the combined list of projected vectors*/
			String combinedProjectedMxFilePath = getSystemCombinedProjectedMxFilePath();
			String fullMxPath = "src/thmp/data/"+TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx";
			
			if(null != servletContext){				
				pathToProjectionMx = servletContext.getRealPath(pathToProjectionMx);
				combinedProjectedMxFilePath = servletContext.getRealPath(combinedProjectedMxFilePath);
				fullMxPath = servletContext.getRealPath(fullMxPath);
			}
			
			evaluateWLCommand(ml, "<<"+combinedProjectedMxFilePath, false, true);
			ml.evaluate("<<" + pathToProjectionMx+";");
			ml.discardAnswer();
			
			if(USE_FULL_MX){
				evaluateWLCommand(ml, "<<"+fullMxPath);
			}
			//need both Projection mx and the matrices containing row vectors corresponding to lists!
			
			ml.evaluate("AppendTo[$ContextPath, \""+ TermDocumentMatrix.PROJECTION_MX_CONTEXT_NAME +"\"];");
			ml.discardAnswer();			
			
			if(USE_FULL_MX){
				V_MX = TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME;
				//make rows be theorems
				evaluateWLCommand(ml, V_MX + "= Transpose["+ V_MX + "]");
				evaluateWLCommand(ml, combinedTDMatrixRangeListName + "= Range[Length["+V_MX+"]];"
						+ V_MX + "= Normal["+V_MX+"]", false, true);				
				System.out.println("FULL DIM MX LEN (num thms) " + evaluateWLCommand(ml, "Length["+combinedTDMatrixRangeListName+"]", true, true));
			}else{
				V_MX = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
				evaluateWLCommand(ml, combinedTDMatrixRangeListName + "= Range[Dimensions["+V_MX+"][[1]]]", false, true);
			}
			
			/*String vMx;
			if(USE_FULL_MX){
				vMx = TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME;
				evaluateWLCommand(ml, vMx + "= Transpose["+ vMx + "]");
			}else{
				vMx = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
			}*/
			//ml.evaluate("Nearest["+vMx+"->Range[Dimensions["+vMx+"][[1]]]
			//should uncompress using this code here.
			
			//ml.evaluate("Length[corMx[[1]]]");
			ml.evaluate("Length[mx]");
			ml.waitForAnswer();			
			try{
				vector_vec_length = ml.getExpr().asInt();
				String msg1 = "ThmSearch - mx row dimension (num of words): " + vector_vec_length;
				System.out.println(msg1);
				logger.info(msg1);
			}catch(ExprFormatException e){
				String msg1 = "ExprFormatException when getting row dimension! " + e.getMessage();
				logger.error(msg1);
				throw new IllegalStateException(msg1);
			}
			
		}catch(MathLinkException e){
			msg = "MathLinkException when loading mx file!";
			logger.error(msg + e);
			throw new IllegalStateException(msg, e);
		}
		QUERY_VEC_LENGTH = vector_vec_length;
	}
	
	public static int getQUERY_VEC_LENGTH(){
		return QUERY_VEC_LENGTH;
	}
	
	/**
	 * Constructs the String query to be evaluated.
	 * Submits it to kernel for evaluation.
	 * @param queryVecStr should be a vec and *not* English words, i.e. {{v1, v2, ...}}!
	 * @param num is number of results to show.
	 * @return List of indices of nearest thms. Indices in, eg MathObjList 
	 * (Indices in all such lists should coincide).
	 * @throws MathLinkException 
	 * @throws ExprFormatException 
	 */
	public static List<Integer> findNearestVecs(String queryVecStr, int ... num){
		try{
			//ml.evaluate("corMx");
			//System.out.println("thmsearch - corMx : " + ml.getExpr());
			String msg = "Transposing and applying corMx...";
			logger.info(msg);
			//process query first with corMx. Convert to column vec.
			
			ml.evaluate("queryVecStrTranspose= Transpose[" + queryVecStr + "]; "
					+ "q0 = queryVecStrTranspose + 0.08*corMx.queryVecStrTranspose;");
			boolean getQ = false;
			if(getQ){
				ml.waitForAnswer();
				Expr qVec = ml.getExpr();
				logger.info("ThmSearch - transposed queryVecStr: " + qVec);				
			}else{
				ml.discardAnswer();
			}
			
			//ml.evaluate("corMx");
			//System.out.println("corMx:" +ml.getExpr());
			//ml.waitForAnswer();
			//Expr qVec = ml.getExpr();
			//System.out.println("ThmSearch - qVec: " + qVec);
			//System.out.println("QUERY " + ml.getExpr().part(1));
			msg = "Applied correlation matrix to querty vec, about to Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue}) ";
			System.out.println(msg);
			logger.info(msg);
			
			//ml.evaluate("Length[q]");
			//Expr qVecDim = ml.getExpr();
			//System.out.println("ThmSearch - queryStr: " + queryVecStr);
			
			//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
			//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"/.{0.0->mxMeanValue}];");
			//this step is costly! Don't compute inverse each time!
			//ml.evaluate("q = dInverse.uTranspose.q;");
			//q no longer has 0.0 entries cause 0.1*corMx has been added. <--could still have 0.0 entries
			//ml.evaluate("q = dInverse.uTranspose.(q/.{0.0->mxMeanValue});");
			//ml.evaluate("q = Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue});");
			
			//When queries are entered in quick succession,
			//QueryVecStr has wrong dimension?! Same dim as row/col dim of matrix d!!
			//ml.evaluate("q = Inverse[d].Transpose[u].(q/.{0.0->mxMeanValue})");
			//ml.evaluate("q = dInverse.uTranspose.(q0/.{0.0->mxMeanValue})");
			if(!USE_FULL_MX){
				/*q is column vector*/
				ml.evaluate("q = dInverse.uTranspose.q0;");
				//ml.discardAnswer();			
				getQ = false;
				if(getQ){
					ml.waitForAnswer();
					Expr qVec = ml.getExpr();
					logger.info("ThmSearch - dInverse.uTranspose.(q0/.{0.0->mxMeanValue}): " + qVec);
					//System.out.println("qVec: " + qVec);
				}else{				
					ml.discardAnswer();
				}
				
				if(DEBUG){
					System.out.println("The Nontrivial Values in query vec: " + evaluateWLCommand(ml, 
							"q1=Transpose[q][[1]]; pos=Position[q1, Except[0.]]; Map[Part[q1, #]&, pos]", true, true));
				}
			}else{
				evaluateWLCommand(ml,"q = q0");
				System.out.println("Nontrivial Pos: " + evaluateWLCommand(ml, 
						"q1=Transpose[q0][[1]]; pos=Position[q1, Except[0.]]", true, true));
				System.out.println("Values at Pos: " + evaluateWLCommand(ml, 
						"Map[Part[q1, #]&, pos]", true, true));
			}
			
			//ml.waitForAnswer();
			//System.out.println("ThmSearch - q after inverse of transpose: " + ml.getExpr());
			/*ml.evaluate("q = q + vMeanValue;");
			ml.discardAnswer();*/
			//System.out.println("q + vMeanValue: " + ml.getExpr());
		}catch(MathLinkException e){
			throw new IllegalStateException(e);
		}
		//vMeanValue
		//use Nearest to get numNearest number of nearest vectors, 
		int numNearest;
		if(num.length == 0){
			numNearest = NUM_NEAREST;
		}else{
			numNearest = num[0];
		}
		//ml.evaluate("v[[1]]");
		//ml.getExpr();
		//System.out.println("DIMENSIONS " +ml.getExpr());
		
		int[] nearestVecArray;
		try{
			String msg = "Applying Nearest[]...";
			System.out.println(msg);
			logger.info(msg);
			
			if(DEBUG){
				System.out.println("Dimensions[vMx] " + evaluateWLCommand(ml, "Dimensions["+V_MX+"]", true, true));
			}
			//System.out.println("Dimensions@First@Transpose[q] " + evaluateWLCommand(ml, "Dimensions[First@Transpose[q]]", true, true));
			//String vMx = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
			ml.evaluate("Nearest["+V_MX+"->"+ combinedTDMatrixRangeListName +", First@Transpose[q],"+numNearest+"] - " + LIST_INDEX_SHIFT);
			//ml.evaluate("Nearest[v->Range[Dimensions[v][[1]]], First@Transpose[q],"+numNearest+"] - " + LIST_INDEX_SHIFT);
			
			ml.waitForAnswer();
			Expr nearestVec = ml.getExpr();
			//ml.discardAnswer();
			//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
			//turn into list.
			msg = "SVD returned nearestVec! ";
			System.out.println(msg);
			logger.info(msg);
			//use this when using Nearest
			//int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
			 //<--this line generates exprFormatException if sucessive entries are quickly entered.
			//System.out.println("resulting Expr nearestVec: " + nearestVec);
			nearestVecArray = (int[])nearestVec.asArray(Expr.INTEGER, 1);
		}catch(MathLinkException e){
			logger.error("MathLinkException! " + e.getStackTrace());
			throw new RuntimeException(e);
		}catch(ExprFormatException e){
			logger.error("ExprFormatException! " + e.getStackTrace());
			throw new RuntimeException(e);
		}
		Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
		List<Integer> nearestVecList = Arrays.asList(nearestVecArrayBoxed);
		
		//for(int i = nearestVecList.size()-1; i > -1; i--){
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			System.out.println(TriggerMathThm2.getThm(thmIndex));
			//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
		}
		System.out.println("~~~~~");
		//System.out.println("nearestVecList from within ThmSearch.java: " + nearestVecList);
		return nearestVecList;
	}
	
	private static String readInputAndSearch(){
		
		String query = "";
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			query = TriggerMathThm2.createQueryNoAnno(thm);
			if(WordForms.getWhiteEmptySpacePattern().matcher(query).matches()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}
			//processes query				
			findNearestVecs(query);
		}	
		sc.close();	
		return query;
	}
	
	/**
	 * Reads thm one at a time.
	 * @param thm is a thm input String
	 * @param numVecs number of cloests vecs to take
	 * @return list of indices of nearest thms. 
	 */
	public static List<Integer> findNearestThmsInTermDocMx(String thm, int numVec){
		
		List<Integer> nearestVecList = null;		
		String query = TriggerMathThm2.createQueryNoAnno(thm);
		//String msg = "ThmSearch - query String formed. ";
		//logger.info(msg);
		//System.out.println(msg);
		
		if(WordForms.getWhiteEmptySpacePattern().matcher(query).matches()){
			return Collections.emptyList();
		}
		System.out.println("ThmSearch.java: about to call findNearestVecs()");
		//processes query
		nearestVecList = findNearestVecs(query, numVec);
		
		//System.out.print("Within ThmSearch, nearestVecList: " + nearestVecList);
		return nearestVecList;
	}
	
	//helper function that tests various inputs
	private static void present(Expr expr) throws ExprFormatException{
		System.out.print(expr.length());
		System.out.println("matrixQ" + expr.matrixQ());
		System.out.println("asArray" + expr.asArray(Expr.REAL, 2));
		double[][] ar = (double[][])expr.asArray(Expr.INTEGER, 2);
		for(double[] i : ar){
			for(double j : i){
			System.out.print(j +  " ");
		}
			System.out.println();
		}
		
		System.out.println("Dimensions" + expr.dimensions()[0] +" " + expr.dimensions()[1]);
		System.out.println(expr.dimensions().length);
	}	
	
	}//end of class
	
	public static class TermDocumentMatrix{
		
		//private static final String PATH_TO_MX = "FileNameJoin[{Directory[], \"termDocumentMatrixSVD.mx\"}]";		
		private static final String PATH_TO_MX = getSystemProjectionMxFilePath();
		private static final String PROJECTION_MX_CONTEXT_NAME = "TermDocumentMatrix`";
		//protected static final String PROJECTED_MX_CONTEXT_NAME = "ProjectedTDMatrixContext`";
		protected static final String FULL_TERM_DOCUMENT_MX_CONTEXT_NAME = "FullTDMatrix`";
		//projected full term-document matrix, so "v^T" in SVD.
		public static final String PROJECTED_MX_NAME = "ProjectedTDMatrix";
		public static final String FULL_TERM_DOCUMENT_MX_NAME = "FullTDMatrix";
		protected static final String COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME = "CombinedTDMatrix";		
		private static final String D_INVERSE_NAME = "dInverse";
		private static final String U_TRANSPOSE_NAME = "uTranspose";
		private static final String COR_MX_NAME = "corMx";
		public static final String PARSEDEXPRESSION_LIST_FILE_NAME = "parsedExpressionList.dat";
		private static final String COMBINED_PARSEDEXPRESSION_LIST_FILE_NAME = "combinedParsedExpressionList.dat";
		
		private static KernelLink ml;
		/**
		 * Serialize the high-dimensional term-document mx, 
		 * as formed from one run of DetectHypothesis.java.  
		 * @param HighDimTDMatrix presented as *Sparse*Array's.
		 * fullTermDocumentMxPath such as "0208_001/0208/FullTDMatrix.mx".
		 */
		public static void serializeHighDimensionalTDMx(ImmutableList<TheoremContainer> defThmList,
				String fullTermDocumentMxPath, Map<String, Integer> thmWordsFreqMap){			
			//String HighDimTDSparseMatrix, 
			/*ml = MathLinkFactory.createKernelLink(ARGV);
			System.out.println("MathLink created! "+ ml);
			//discard initial pakets the kernel sends over.
			ml.discardAnswer();*/
			ml = FileUtils.getKernelLinkInstance();

			//int rowDimension = docMx.length;
			int rowDimension = thmWordsFreqMap.size();
			//int mxColDim = docMx[0].length;
			int mxColDim = defThmList.size();
			
			//set up the matrix corresponding to docMx, to be SVD'd. 
			//adjust mx entries based on correlation first	
			//StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
			//mxSB.append(toNestedList(docMx)).append("//N;");
			/* *Need* to specify dimension! Since the Automatic dimension might be less than 
			 * or equal to the size of the keywordList, if some words are not hit by the current thm corpus. */
			StringBuilder mxSB = TriggerMathThm2.sparseArrayInputSB(defThmList, thmWordsFreqMap) 
					.insert(0, "m=SparseArray[1+").append(",{"+rowDimension).append(",").append(mxColDim+"}];");
			//System.out.println("ThmSearch. - mxSB " + mxSB);
			String msg = "ThmSearch.TermDocumentMatrix - mxSB.length(): " + mxSB.length();
			System.out.println(msg);
			logger.info(msg);
			
			//ml.evaluate(mxSB.toString()); //mxSB.append(";") here causes memory bloat?!			
			evaluateWLCommand(FULL_TERM_DOCUMENT_MX_NAME + "=" + mxSB.toString());
			//System.out.println("ThmSearch.TermDocumentMatrix.SparseArray formed: " + mxSB);
			
			msg = "Kernel has the matrix!";
			logger.info(msg);
			System.out.println(msg);
			//don't use special context for now, March 2017
			evaluateWLCommand("DumpSave[\"" + fullTermDocumentMxPath + "\"," + FULL_TERM_DOCUMENT_MX_NAME + "]");
		}
		
		/**
		 * Projects the full TD mx down to lower dimension given previosly created SVD matrices.
		 * @param fullTermDocumentMxPath path to full high-dim term document mx. e.g. 
		 * "0208_001/0208/FullTDMatrix.mx" 
		 * @param projectionMxPath Path to mx file with projection matrix context, created from previous SVD.
		 * @param termDocumentMxPath Path to projected term document matrix (as row vectors), e.g. 
		 * "0208_001/0208/ProjectedTDMatrix.mx"
		 */
		public static void projectTermDocumentMatrix(String fullTermDocumentMxPath, String projectionMxPath, 
				String projectedTermDocumentMxPath) {	
			String msg = "ThmSearch.TermDocumentMatrix.projectTermDocumentMatrix - starting projection";
			System.out.println(msg);
			logger.info(msg);
			evaluateWLCommand("<<"+projectionMxPath + "; AppendTo[$ContextPath, \"" + PROJECTION_MX_CONTEXT_NAME + "\"]");
			//full mx that was DumpSave'd from one tar file.
			//may not be using context!
			evaluateWLCommand("<<"+fullTermDocumentMxPath// + "; AppendTo[$ContextPath," + PROJECTED_MX_CONTEXT_NAME + "]"
					);	
			//evaluateWLCommand(PROJECTED_MX_CONTEXT_NAME , true, false);
			String fullTDMxName = FULL_TERM_DOCUMENT_MX_NAME;
			String dInverseName = D_INVERSE_NAME;
			String uTransposeName = U_TRANSPOSE_NAME;
			String corMxName = COR_MX_NAME;
			ProjectionMatrix.applyProjectionMatrix(fullTDMxName, dInverseName, uTransposeName, 
					corMxName, PROJECTED_MX_NAME);
			evaluateWLCommand("DumpSave[\"" + projectedTermDocumentMxPath + "\", "+ PROJECTED_MX_NAME+ "]");
			msg = "ThmSearch.TermDocumentMatrix.projectTermDocumentMatrix - Done projecting";
			System.out.println(msg);
			logger.info(msg);
		}
		
		public static void createTermDocumentMatrixSVD() {	
			//docMx = TriggerMathThm2.mathThmMx();			
			//mx to keep track of correlations between terms, mx.mx^T
			//List<List<Integer>> corMxList = new ArrayList<List<Integer>>();
			try{			
				/*ml = MathLinkFactory.createKernelLink(ARGV);
				System.out.println("MathLink created! "+ ml);
				//discard initial pakets the kernel sends over.
				ml.discardAnswer();*/
				ml = FileUtils.getKernelLinkInstance();
				String msg = "Kernel instance acquired...";
				logger.info(msg);
				//int rowDimension = docMx.length;
				int rowDimension = TriggerMathThm2.mathThmMxRowDim();
				//int mxColDim = docMx[0].length;
				int mxColDim = TriggerMathThm2.mathThmMxColDim();
				System.out.println("ThmSearch-TermDocumentMatrix - number of keywords: " + rowDimension);
				System.out.println("ThmSearch-TermDocumentMatrix - number of theorems: " + mxColDim);
				//set up the matrix corresponding to docMx, to be SVD'd.
				//adjust mx entries based on correlation first	
				//StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
				//mxSB.append(toNestedList(docMx)).append("//N;");
				/* *Need* to specify dimension! Since the Automatic dimension might be less than 
				 * or equal to the size of the keywordList, if some words are not hit by the current thm corpus. */
				StringBuilder mxSB = TriggerMathThm2.sparseArrayInputSB()
						.insert(0, "m=SparseArray[1+").append(",{"+rowDimension).append(",").append(mxColDim+"}];");
				//System.out.println("ThmSearch. - mxSB " + mxSB);
				msg = "ThmSearch.TermDocumentMatrix - mxSB.length(): " + mxSB.length();
				System.out.println(msg);
				logger.info(msg);
				
				//System.out.println("nested mx " + Arrays.deepToString(docMx));
				
				ml.evaluate(mxSB.toString()); //mxSB.append(";") here causes memory bloat?!
				
				msg = "Kernel has the matrix!";
				logger.info(msg);
				System.out.println(msg);
				
				boolean getMx = false;
				if(getMx){
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("m " + expr);
				}else{	
					ml.discardAnswer();	
				}
				ml.evaluate("Begin[\""+ PROJECTION_MX_CONTEXT_NAME +"\"];");
				ml.discardAnswer();
				
				//corMx should be computed using correlation mx
				//or add a fraction of M.M^T.M
				//this has the effect that if ith term and jth terms
				//are correlated, and (i,k) is non zero in M, then make (j,k)
				//nonzero (of smaller magnitude than (i,K) in M.
				//clip the matrix 			
				boolean getMean = false;
				if(getMean){
					ml.evaluate("matrix = m.Transpose[m].m;");
					ml.discardAnswer();
					ml.evaluate("Mean[matrix//N]");
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("Mean " + expr);
				}
				
				//ml.evaluate("corMx = Clip[ m.Transpose[m], {4, Infinity}, {0, 0} ].m;");
				//ml.evaluate("correlatedMx = IntegerPart[m.Transpose[m].m];");
				//ml.discardAnswer();			
				
				//System.out.println("Done clipping!");	
				boolean getCorMx = false;
				
				//the entries in clipped correlation are between 0.3 and 1.
				//subtract IdentityMatrix to avoid self-compounding
				ml.evaluate("corMx = Clip[Correlation[Transpose[m]]-IdentityMatrix[" + rowDimension 
						+ "], {.6, Infinity}, {0, 0}]/.Indeterminate->0.0;");			
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					//get correlation matrix, to put together correlated terms
					//in similar indices. 
					System.out.println("corMx " + expr);					
				}else{
					ml.discardAnswer();
				}
				
				msg = "Corr. mx clipped! Ready to add corMx to m.";
				logger.info(msg);
				System.out.println(msg);
				
				
				//the entries in corMx.m can range from 0 to ~6
				ml.evaluate("mx = m + .15*corMx.m;");
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					System.out.println("m + .15*corMx.m " + expr);
				}else{
					ml.discardAnswer();
				}				
				System.out.println("ThmSearch - cor matrix added!");
				
				
				//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
				String dimMsg = "Dimensions of docMx: " + rowDimension + " " +mxColDim + ". Starting SVD...";
				System.out.print(dimMsg);
				logger.info(dimMsg);
				
				
				/*ml.evaluate("Mean[Mean[0.2*Transpose[ Covariance[Transpose[mx]].mx ]]]");
				//ml.evaluate("mx");
				ml.waitForAnswer();
				Expr mean = ml.getExpr();
				System.out.print("Mean of Mean " + mean);
				
				//ml.evaluate("mx = mx + Clip[0.2*Transpose[mx.Correlation[Transpose[mx]]], {2, Infinity}, {0, 0}];");
				ml.evaluate("mx = mx + Clip[0.2*Transpose[Covariance[Transpose[mx]].mx], {.1, Infinity}, {0, 0}];");
				ml.discardAnswer(); */
				
				//trim down to make mx sparse again, and also don't want small correlations
				//ml.evaluate("mx = Clip[mx, {.2, Infinity}, {0, 0}];");
				//ml.discardAnswer();		
				
				//System.out.println("Done clipping");
				
				//add a small multiple of mx.mx^T.mx, so to make term i more 
				//prominent when a correlated term is present.
				//ml.evaluate("mx=mx+0.2*mx.Transpose[mx].mx;");
				//this was to take correlations into account
				//ml.evaluate("mx=mx.Transpose[mx].mx;");
				//ml.discardAnswer();
				
				//number of singular values to keep. Determined (roughly) based on the number of
				//theorems (col dimension of mx)
				//int k = NUM_SINGULAR_VAL_TO_KEEP;
				int k = mxColDim < 35 ? mxColDim : (mxColDim < 400 ? 35 : (mxColDim < 1000 ? 40 : (mxColDim < 3000 ? 50 : 60)));
				ml.evaluate("ClearSystemCache[]; {u, d, v} = SingularValueDecomposition[mx, " + k +"];");
				//ml.waitForAnswer();
				ml.discardAnswer();
				/*ml.evaluate("m");
				ml.waitForAnswer();
				System.out.println("!!!!-----m: " + ml.getExpr() + " k: " + k + " mxColDim: " + mxColDim);*/
				
				//ml.evaluate("u = u; dd = d; v = v;");
				//ml.discardAnswer();
				System.out.println("Finished SVD");
				logger.info("Finished SVD!");
				//randomly select column vectors to approximate mean
				//adjust these!
				int numRandomVecs = mxColDim < 500 ? 60 : (mxColDim < 5000 ? 100 : 150);
				ml.evaluate("mxMeanValue = Mean[Flatten[mx[[All, #]]& /@ RandomInteger[{1,"+ mxColDim +"}," + numRandomVecs + "]]];"
						+ "dInverse=Inverse[d]; uTranspose=Transpose[u];"
						);				
				//ml.evaluate("mxMeanValue = Mean[Flatten[v]];");
				ml.discardAnswer();
				//System.out.println("mxMeanValue " + ml.getExpr());
				/*ml.evaluate("mxMeanValue = Mean[Flatten[mx]];");
				ml.discardAnswer();	*/
				//System.out.println(" mean of flattened mx " + ml.getExpr().part(1));
				
				/*for(int i = 1; i <= docMx[0].length; i++){
				//should just be columns of V*, so rows of V
					ml.evaluate("p" + i + "= v[["+i+"]];");
					ml.discardAnswer();
				}*/
				
				//String queryStr = TriggerMathThm2.createQuery("root");
				//System.out.println(queryStr);
				//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
				//ml.discardAnswer();
				/*ml.evaluate("q//N");
				ml.waitForAnswer();
				Expr v = ml.getExpr();
				System.out.println("exprs realQ? " + v); 
				
				ml.evaluate("(p1.First@Transpose[q])//N");
				ml.waitForAnswer();
				Expr w = ml.getExpr();
				System.out.println("~W " + w);*/
				ml.evaluate("End[];");
				ml.discardAnswer();
				
				ml.evaluate("DumpSave[\"" + PATH_TO_MX + "\", \"TermDocumentMatrix`\"]");
				ml.discardAnswer();
			}catch(MathLinkException e){
				System.out.println("error at launch!");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		
		/**
		 * @param defThmList This instead of list of strings, so not to take up additional memory
		 * storing just Strings.
		 */
		public static void createTermDocumentMatrixSVD(ImmutableList<TheoremContainer> defThmList) {			
			//docMx = TriggerMathThm2.mathThmMx();			
			//mx to keep track of correlations between terms, mx.mx^T
			//List<List<Integer>> corMxList = new ArrayList<List<Integer>>();
			try{			
				/*ml = MathLinkFactory.createKernelLink(ARGV);
				System.out.println("MathLink created! "+ ml);
				//discard initial pakets the kernel sends over.
				ml.discardAnswer();*/
				ml = FileUtils.getKernelLinkInstance();
				String msg = "Kernel instance acquired...";
				logger.info(msg);
				//int rowDimension = docMx.length;
				int rowDimension = TriggerMathThm2.mathThmMxRowDim();
				//int mxColDim = docMx[0].length;
				int mxColDim = defThmList.size();
				System.out.println("ThmSearch-TermDocumentMatrix - number of keywords: " + rowDimension);
				System.out.println("ThmSearch-TermDocumentMatrix - number of theorems: " + mxColDim);
				//set up the matrix corresponding to docMx, to be SVD'd. 
				//adjust mx entries based on correlation first	
				//StringBuilder mxSB = new StringBuilder("m = Developer`ToPackedArray@");
				//mxSB.append(toNestedList(docMx)).append("//N;");
				/* *Need* to specify dimension! Since the Automatic dimension might be less than 
				 * or equal to the size of the keywordList, if some words are not hit by the current thm corpus. */
				StringBuilder mxSB = TriggerMathThm2.sparseArrayInputSB(defThmList)
						.insert(0, "m=SparseArray[1+").append(",{"+rowDimension).append(",").append(mxColDim+"}];");
				//System.out.println("ThmSearch. - mxSB " + mxSB);
				msg = "ThmSearch.TermDocumentMatrix - mxSB.length(): " + mxSB.length();
				System.out.println(msg);
				logger.info(msg);
				
				ml.evaluate(mxSB.toString()); //mxSB.append(";") here causes memory bloat?!
				
				msg = "Kernel has the matrix!";
				logger.info(msg);
				System.out.println(msg);
				
				boolean getMx = false;
				if(getMx){
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("m " + expr);
				}else{	
					ml.discardAnswer();	
				}
				ml.evaluate("Begin[\""+ PROJECTION_MX_CONTEXT_NAME +"\"];");
				ml.discardAnswer();
				
				//corMx should be computed using correlation mx
				//or add a fraction of M.M^T.M
				//this has the effect that if ith term and jth terms
				//are correlated, and (i,k) is non zero in M, then make (j,k)
				//nonzero (of smaller magnitude than (i,K) in M.
				//clip the matrix 			
				boolean getMean = false;
				if(getMean){
					ml.evaluate("matrix = m.Transpose[m].m;");
					ml.discardAnswer();
					ml.evaluate("Mean[matrix//N]");
					ml.waitForAnswer();			
					Expr expr = ml.getExpr();
					System.out.println("Mean " + expr);
				}
				
				//ml.evaluate("corMx = Clip[ m.Transpose[m], {4, Infinity}, {0, 0} ].m;");
				//ml.evaluate("correlatedMx = IntegerPart[m.Transpose[m].m];");
				//ml.discardAnswer();
				
				boolean printCorMx = false;
				if(printCorMx){
					ml.evaluate("Transpose[m]");
					ml.waitForAnswer();
					System.out.println("ThmSearch - [Transpose[m]]: " + ml.getExpr());
				}
				//System.out.println("Done clipping!");	
				boolean getCorMx = false;
				
				/*The entries in clipped correlation are between 0.3 and 1. Correlation between *words*,
				  not theorems. */
				//subtract IdentityMatrix to avoid self-compounding
				ml.evaluate("corMx = Clip[Correlation[Transpose[m]]-IdentityMatrix[" + rowDimension 
						+ "], {.6, Infinity}, {0, 0}]/.Indeterminate->0.0;");
				
				//ml.waitForAnswer();
				//System.out.println("clipped corr mx: " + ml.getExpr());
				
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					//get correlation matrix, to put together correlated terms
					//in similar indices. 
					System.out.println("corMx " + expr);					
				}else{
					ml.discardAnswer();
				}
				msg = "Corr. mx clipped! Ready to add corMx to m.";
				logger.info(msg);
				System.out.println(msg);
				
				//the entries in corMx.m can range from 0 to ~6
				ml.evaluate("mx = m + .15*corMx.m;");
				if(getCorMx){
					ml.waitForAnswer();
					Expr expr = ml.getExpr();
					System.out.println("ThmSearch - m + .15*corMx.m " + expr);
				}else{
					ml.discardAnswer();
				}
				
				
				//System.out.println("is matrix? " + r.part(1).matrixQ() + r.part(1));
				//System.out.println(Arrays.toString((int[])r.part(1).part(1).asArray(Expr.INTEGER, 1)));
				
				/*int corMxLen1 = r.part(1).length();
				for(int i = 0; i < corMxLen1; i++){
					Integer[] thm_iListBoxed = ArrayUtils.toObject((int[])r.part(1).part(i+1).asArray(Expr.INTEGER, 1));
					List<Integer> thm_iList = Arrays.asList(thm_iListBoxed);
					corMxList.add(thm_iList);
				}*/
				//adjust entries of docMx based on corMxList
				//do this in WL, not loops here!
				//int[][] corrAdjustedDocMx = corrAdjustDocMx(docMx, corMxList);
				//mx = toNestedList(corrAdjustedDocMx);
				
				//write matrix to file, so no need to form it each time
				
				//System.out.println(nearestVec.length() + "  " + Arrays.toString((int[])nearestVec.part(1).asArray(Expr.INTEGER, 1)));
				String dimMsg = "Dimensions of docMx: " + rowDimension + " " +mxColDim + ". Starting SVD...";
				System.out.print(dimMsg);
				logger.info(dimMsg);
				//System.out.println(mx);
				
				//ml.evaluate("mx=" + mx +"//N;");
				//ml.discardAnswer();	
				
				/*ml.evaluate("Mean[Mean[0.2*Transpose[ Covariance[Transpose[mx]].mx ]]]");
				//ml.evaluate("mx");
				ml.waitForAnswer();
				Expr mean = ml.getExpr();
				System.out.print("Mean of Mean " + mean);
				
				//ml.evaluate("mx = mx + Clip[0.2*Transpose[mx.Correlation[Transpose[mx]]], {2, Infinity}, {0, 0}];");
				ml.evaluate("mx = mx + Clip[0.2*Transpose[Covariance[Transpose[mx]].mx], {.1, Infinity}, {0, 0}];");
				ml.discardAnswer(); */
				
				//trim down to make mx sparse again, and also don't want small correlations
				//ml.evaluate("mx = Clip[mx, {.2, Infinity}, {0, 0}];");
				//ml.discardAnswer();		
				
				//System.out.println("Done clipping");
				
				//add a small multiple of mx.mx^T.mx, so to make term i more 
				//prominent when a correlated term is present.
				//ml.evaluate("mx=mx+0.2*mx.Transpose[mx].mx;");
				//this was to take correlations into account
				//ml.evaluate("mx=mx.Transpose[mx].mx;");
				//ml.discardAnswer();
				
				//number of singular values to keep. Determined (roughly) based on the number of
				//theorems (col dimension of mx)
				//int k = NUM_SINGULAR_VAL_TO_KEEP;
				int minDim = 40;
				int k = mxColDim < minDim ? mxColDim : (mxColDim < 400 ? minDim : (mxColDim < 1000 ? 45 : (mxColDim < 3000 ? 50 : 60)));
				ml.evaluate("ClearSystemCache[]; {u, d, v} = SingularValueDecomposition[mx, " + k +"];");
				//ml.waitForAnswer();
				ml.discardAnswer();
				/*ml.evaluate("m");
				ml.waitForAnswer();
				System.out.println("!!!!-----m: " + ml.getExpr() + " k: " + k + " mxColDim: " + mxColDim);*/
				
				//ml.evaluate("u = u; dd = d; v = v;");
				//ml.discardAnswer();
				System.out.println("Finished SVD");
				logger.info("Finished SVD!");
				//randomly select column vectors to approximate mean
				//adjust these!
				int numRandomVecs = mxColDim < 500 ? 60 : (mxColDim < 5000 ? 100 : 150);
				ml.evaluate("mxMeanValue = Mean[Flatten[mx[[All, #]]& /@ RandomInteger[{1,"+ mxColDim +"}," + numRandomVecs + "]]];"
						+ "dInverse=Inverse[d]; uTranspose=Transpose[u];"
						);				
				//ml.evaluate("mxMeanValue = Mean[Flatten[v]];");
				ml.discardAnswer();
				//System.out.println("mxMeanValue " + ml.getExpr());
				/*ml.evaluate("mxMeanValue = Mean[Flatten[mx]];");
				ml.discardAnswer();	*/
				//System.out.println(" mean of flattened mx " + ml.getExpr().part(1));
				
				/*for(int i = 1; i <= docMx[0].length; i++){
				//should just be columns of V*, so rows of V
					ml.evaluate("p" + i + "= v[["+i+"]];");
					ml.discardAnswer();
				}*/
				
				//String queryStr = TriggerMathThm2.createQuery("root");
				//System.out.println(queryStr);
				//ml.evaluate("q = Inverse[d].Transpose[u].Transpose["+queryStr+"];");
				//ml.discardAnswer();
				/*ml.evaluate("q//N");
				ml.waitForAnswer();
				Expr v = ml.getExpr();
				System.out.println("exprs realQ? " + v); 
				
				ml.evaluate("(p1.First@Transpose[q])//N");
				ml.waitForAnswer();
				Expr w = ml.getExpr();
				System.out.println("~W " + w);*/
				ml.evaluate("End[];");
				ml.discardAnswer();
				
				ml.evaluate("DumpSave[\"" + PATH_TO_MX + "\", \"TermDocumentMatrix`\"]");
				ml.discardAnswer();
			}catch(MathLinkException e){
				System.out.println("error at launch!");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		
	}/* end of TermDocumentMatrix class */
	
	/**
	 * Adjusts docMx based on corMxList: increase docMx[j][k] if corMxList.get(i).get(j)
	 * is high.
	 * @param docMx
	 * @param corMxList
	 * @return
	 * @deprecated
	 */
	private static int[][] corrAdjustDocMx(int[][] docMx, List<List<Integer>> corMxList){
		int docMxDim1 = docMx.length;
		int docMxDim2 = docMx[0].length;
		//must create new mx, since modifying docMx in place will mess up updates 
		int[][] corrDocMx = new int[docMxDim1][docMxDim2];
		
		//System.out.println("b=" +toNestedList(docMx));
		
		for(int i = 0; i < docMxDim1; i++){
			for(int k = 0; k < docMxDim2; k++){
				if(docMx[i][k] != 0){
					corrDocMx[i][k] = docMx[i][k];
					for(int j = 0; j < docMxDim1; j++){
						if(corMxList.get(i).get(j) > COR_THRESHOLD){
							//for ~1100 thms, /2 is too much addition, can skew results, /3 seems ok.
							corrDocMx[j][k] += Math.max(docMx[i][k]/3.0, .5); 
						}
					}
				}
			}
		}
		return corrDocMx;
	}
	/**
	 * Convert docMx from array form to a String
	 * that's a nested List for WL.
	 * @deprecated
	 */
	private static String toNestedList(double[][] docMx){
		StringBuilder sb = new StringBuilder();
		//hString s = "";
		//s += "{";
		sb.append("{");
		
		int docSz = docMx.length;
		for(int i = 0; i < docSz; i++){
			//s += "{";
			sb.append("{");
			int iSz = docMx[i].length;
			for(int j = 0; j < iSz; j++){
				String t = j == iSz-1 ? docMx[i][j] + "" : docMx[i][j] + ", ";
				//s += t;
				sb.append(t);
			}
			String t = i == docSz-1 ? "}" : "}, ";
			//s += t;
			sb.append(t);
		}
		//s += "}";
		sb.append("}");
		return sb.toString();
	}
	
	
	public static void main(String[] args) {		
		
		KernelLink ml = FileUtils.getKernelLinkInstance();
		try{			
			//String result = ml.evaluateToOutputForm("Transpose@" + toNestedList(docMx), 0);
			//String result = ml.evaluateToOutputForm("4+4", 0);
			//result = ml.evaluateToOutputForm("IdentityMatrix[2]", 0);
			//result = ml.evaluateToOutputForm("Plus@@{4,2}", 0);
			//ml.evaluate("Transpose[{{1, 2},{3,4}}]");
			//ml.evaluate("SingularValueDecomposition@" + toNestedList(docMx) +"//N");
			
			//ml.evaluate("Transpose@{{1,2},{3,4}}");
			/*ml.evaluate("d1 = 3;");
			ml.discardAnswer();			
			ml.evaluate("d1+2");
			ml.waitForAnswer();
			Expr expr = ml.getExpr();			
			System.out.println(expr);
			
			//System.out.println(Arrays.toString(expr.dimensions()));
			System.out.println(expr.integerQ());*/

			System.out.println("~~~");
			//reads input theorem, generates query string, process query
			ThmSearchQuery.readInputAndSearch();
			
		}catch(IndexOutOfBoundsException e){			
			logger.error("IndexOutOfBoundsException during evaluation using MathLink." + e.getStackTrace());
			//e.printStackTrace();
			throw new IllegalStateException("IndexOutOfBoundsException during evaluation!", e);
		}
		finally{
			ml.close();
		}		
	}
	
	/**
	 * Retrieves path to mx file containing SVD matrix data, 
	 * Supports Linux and OS X. 
	 * @return
	 */
	private static String getSystemProjectionMxFilePath(){
		String pathToMx = "src/thmp/data/termDocumentMatrixSVD.mx";
		//mx file also depends on the system!		
		//but only 32-bit vs 64-bit, not OS. Should check bit instead.
		
		/*String OS_name = System.getProperty("os.name");
		if(OS_name.equals("Mac OS X")){
			pathToMx = "src/thmp/data/termDocumentMatrixSVDmac.mx";
		}*/
		return pathToMx;
	}
	
	/**
	 * Retrieves path to matrix combining projected vecs.
	 * @return
	 */
	private static String getSystemCombinedProjectedMxFilePath(){
		String pathToMx = "src/thmp/data/" + TermDocumentMatrix
				.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME + ".mx";
		return pathToMx;
	}
	
	/**
	 * Retrieves path to combined parsedExpressionList.dat.
	 * @return
	 */
	protected static String getSystemCombinedParsedExpressionListFilePath(){
		String pathToCombinedParsedExpressionListPath = "src/thmp/data/"
				+TermDocumentMatrix.COMBINED_PARSEDEXPRESSION_LIST_FILE_NAME;
		return pathToCombinedParsedExpressionListPath;
	}
	
}
