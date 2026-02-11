package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
	private int mainPc;
	
	public int getMainPc() {
		return mainPc;
	}
	
	/* LOG MESSAGES */
	private boolean errorDetected = false;
	Logger log = Logger.getLogger(getClass());
	
	public void report_error(String message, SyntaxNode info) {
		errorDetected  = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.info(msg.toString());
	}
	
	public CodeGenerator() {
		{	// chr/ord
			Tab.ordObj.setAdr(Code.pc);
			Tab.chrObj.setAdr(Code.pc);
			Code.put(Code.enter);
			Code.put(1);
			Code.put(1);
			Code.put(Code.load_n);
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
		
	}
	
	@Override
	public void visit(MethodName methodName) {
		if (methodName.getI1().equals("main")) {
			VirtualMethodTable.generateCode();
			mainPc = Code.pc;
		}
		
		methodName.obj.setAdr(Code.pc);
		
		Code.put(Code.enter);
		Code.put(methodName.obj.getLevel());
		Code.put(methodName.obj.getLocalSymbols().size());
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(DsgArrayName dsgArrayName) {
		Code.load(dsgArrayName.obj);
	}
	
	@Override
	public void visit(DsgScopeName dsgScopeName) {
		if (dsgScopeName.obj.getKind() != Obj.Type)
			Code.load(dsgScopeName.obj);
	}
	
	@Override
	public void visit(DsgScopeMore_var dsgScopeMore_var) {		
		SyntaxNode par = dsgScopeMore_var.getParent();
		if(par instanceof Designator_scope_elem 
				|| par instanceof DsgScopeMore_scope_elem)
			Code.load(dsgScopeMore_var.obj);
	}
	
	@Override
	public void visit(DsgScopeMore_elem dsgScopeMore_elem) {
		SyntaxNode parent = dsgScopeMore_elem.getParent();
		if(parent instanceof DsgScopeMore_scope_var || parent instanceof DsgScopeMore_scope_elem)
			Code.load(dsgScopeMore_elem.obj);
	}
	
	@Override
	public void visit(DsgScopeMore_scope_var dsgScopeMore_scope_var) {
		SyntaxNode parent = dsgScopeMore_scope_var.getParent();
		if(parent instanceof DsgScopeMore_scope_var || parent instanceof DsgScopeMore_scope_elem)
			Code.load(dsgScopeMore_scope_var.obj);
	}
	
	@Override
	public void visit(DsgScopeMore_scope_elem dsgScopeMore_scope_elem) {
		SyntaxNode parent = dsgScopeMore_scope_elem.getParent();
		if(parent instanceof DsgScopeMore_scope_var || parent instanceof DsgScopeMore_scope_elem)
			Code.load(dsgScopeMore_scope_elem.obj);
	}
	
	@Override
	public void visit(DsgScopeArrayName dsgScopeArrayName) {
		Code.load(dsgScopeArrayName.obj);
	}
	
	/*
	@Override
	public void visit(Designator_simple d) {
		SyntaxNode parent = d.getParent();
		if (parent.getClass() != DesignatorStatement_assign.class
				// e.g. arr = new int[10]; Sluzi da se levi arr ne pushuje
				&& (parent.getClass() != Statement_read.class || d.obj.getType().getKind() == Struct.Array)
				// e.g. read(a); Ne treba pushovati a na stek; To se resava Obj-ovima
				&& d.obj.getKind() != Obj.Meth
				// e.g. m(); Ne treba da pushuje m kao ime metode
				&& d.obj.getKind() != Obj.Type
				// e.g. x = EnumName.ELEM; pokusao bi load od EnumName
				&& (parent.getClass() != Designator_dot.class || parent.getParent().getClass() != MethodInvokeName.class)
				
		){
			Code.load(d.obj);	
		}
	}
	
	@Override
	public void visit(Designator_dot d) {
		SyntaxNode parent = d.getParent();
		if (d.obj.getName().equals("arr.len")) {
			Code.put(Code.arraylength);
		} else if (parent.getClass() != DesignatorStatement_assign.class 
				&& parent.getClass() != MethodInvokeName.class){
			Code.load(d.obj);
		}
	}
	
	@Override
	public void visit(Designator_arr d) {
		SyntaxNode parent = d.getParent();
		if (parent.getClass() != DesignatorStatement_assign.class
				&& parent.getClass() != Statement_read.class) {
			Code.load(d.obj);
		}
	}
	*/
	@Override
	public void visit(FactorSub_new factorSub_new) {
		Code.put(Code.new_);
		Code.put2(factorSub_new.struct.getNumberOfFields() * 4);
	}
	
	@Override
	public void visit(FactorSub_meth factorSub_meth) {
		int offset = factorSub_meth.getMethodInvokeName().obj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}
	
	@Override
	public void visit(FactorSub_new_array new_arr) {
		Code.put(Code.newarray);
		// b=0: sizeof(arr[0]) = 1 B
		// b=0: sizeof(arr[0]) = 4 B
		Code.put(new_arr.getType().struct.equals(Tab.charType) ? 0 : 1);
	}
	
	@Override
	public void visit(Factor factor) {
		if(factor.getUnary() instanceof Unary_m) {
			Code.put(Code.neg);
		}
	}
	
	@Override
	public void visit(FactorSub_var factorSub_var) {
		if (factorSub_var.getDesignator().obj.getName().equals("arr.length"))
			Code.put(Code.arraylength);
		else
			Code.load(factorSub_var.getDesignator().obj);
	}
	
	@Override
	public void visit(Literal_n c) {
		Code.loadConst(c.getN1());
	}
	
	@Override
	public void visit(Literal_c c) {
		Code.loadConst(c.getC1());
	}
	
	@Override
	public void visit(Literal_b c) {
		Code.loadConst(c.getB1());
	}
	
	@Override
	public void visit(Statement_read stmt) {
		Struct s = stmt.getDesignator().obj.getType();
		if (s.getElemType() != null) {	// for arr
			s = s.getElemType();
		}
		
		if (s.equals(Tab.intType) || s.equals(Tab.find("bool").getType())) {
			Code.put(Code.read);
		} else if (s.equals(Tab.charType)) {
			Code.put(Code.bread);
		}
		Code.store(stmt.getDesignator().obj);
	}
	
	@Override
	public void visit(Statement_print1 stmt) {
		Struct s = stmt.getExpr().struct;
		if (s.getElemType() != null) {
			s = s.getElemType();
		}
		
		if (s.equals(Tab.intType) || s.equals(Tab.find("bool").getType()) || s.getKind() == Struct.Enum) {
			Code.loadConst(5);
			Code.put(Code.print);
		} else if (s.equals(Tab.charType)) {
			Code.loadConst(1);
			Code.put(Code.bprint);
		}
	}
	
	@Override
	public void visit(Statement_print2 stmt) {
		Struct s = stmt.getExpr().struct;
		if (s.getElemType() != null) {
			s = s.getElemType();
		}
		
		Code.loadConst(stmt.getN2());
		
		if (s.equals(Tab.intType) || s.equals(Tab.find("bool").getType()) || s.getKind() == Struct.Enum) {
			Code.put(Code.print);
		} else if (s.equals(Tab.charType)) {
			Code.put(Code.bprint);
		}
	}
	
	@Override
	public void visit(AddopTermList_add addopTermList) {
		Addop addop = addopTermList.getAddop();
		if (addop instanceof Addop_plus) {
			Code.put(Code.add);
		} else if (addop instanceof Addop_minus) {
			Code.put(Code.sub);
		}
	}
	
	@Override
	public void visit(MulopFactorList_mul mulopFactorList) {
		Mulop mulop = mulopFactorList.getMulop();
		if (mulop instanceof Mulop_mul) {
			Code.put(Code.mul);
		} else if (mulop instanceof Mulop_div) {
			Code.put(Code.div);
		} else if (mulop instanceof Mulop_rem) {
			Code.put(Code.rem);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_inc stmt) {
		Code.load(stmt.getDesignator().obj);
		if(stmt.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
			Code.load(stmt.getDesignator().obj);
		}
		Code.loadConst(1);
		Code.put(Code.add);
		Code.store(stmt.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_dec stmt) {
		Code.load(stmt.getDesignator().obj);
		if(stmt.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
			Code.load(stmt.getDesignator().obj);
		}
		Code.loadConst(1);
		Code.put(Code.sub);
		Code.store(stmt.getDesignator().obj);
	}
	
	@Override
	public void visit(Statement_return1 stmt) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(Statement_return2 stmt) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(DesignatorStatement_assign stmt) {
		if (stmt.getExpr().struct.getKind() == Struct.Class) {
			// TODO: Neki bolji uslov?
			Code.put(Code.dup);
			Code.loadConst(
				VirtualMethodTable.getTableAddress(stmt.getExpr().struct)
			);
			Code.put(Code.putfield);
			Code.put2(0); // 0-th field of each class is vmtp
		}
		
		
		Code.store(stmt.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_meth stmt) {
		Obj obj = stmt.getMethodInvokeName().obj;
		if (obj.getKind() == Obj.Meth) {
			int offset = obj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		} else {
			/*
			int tableStart = VirtualMethodTable.getTableAddress(obj.getType());
			Code.loadConst(tableStart);
			Code.put(Code.invokevirtual);
			String methName = ((Designator_dot) stmt.getMethodInvokeName().getDesignator()).getI2();
			for (int ix = 0; ix < methName.length(); ++ix) {
				Code.put((int)methName.charAt(ix));
			}
			Code.put(Code.const_m1);
			*/
		}
		
		
		if(obj.getType() != Tab.noType)
			Code.put(Code.pop);
		
	}
	
	// Condition
	// svi skokovi su unapred tkd. su svi skokovi za patch adres
	private Stack<Integer> skipCondTerm = new Stack<>();
	private Stack<Integer> skipCondition = new Stack<>();
	private Stack<Integer> skipThen = new Stack<>();
	private Stack<Integer> skipElse = new Stack<>();
	
	private int getRelopCode(Relop r) {
		if (r instanceof Relop_eq)
			return Code.eq;
		if (r instanceof Relop_ne)
			return Code.ne;
		if (r instanceof Relop_le)
			return Code.le;
		if (r instanceof Relop_lt)
			return Code.lt;
		if (r instanceof Relop_ge)
			return Code.ge;
		if (r instanceof Relop_gt)
			return Code.gt;
		return 0;
	}
	
	@Override
	public void visit(CondFact_expr condFact_expr) {
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0); // -> to next CondTerm
		// | to next CondFact
		// V
		skipCondTerm.push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondFact_relop condFact_relop) {
		Code.putFalseJump(getRelopCode(condFact_relop.getRelop()), 0); // -> to next CondTerm
		// | to next CondFact
		// V
		skipCondTerm.push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondTerm condTerm) {
		// ako su stigli na kraj CondFactListe 
		// znaci da su svi uslovi iz nje tacni
		Code.putJump(0); // -> to StmtThen
		skipCondition.push(Code.pc - 2);
		// | next CondFactList
		// V
		while(!skipCondTerm.empty())
			Code.fixup(skipCondTerm.pop());
	}
	
	@Override
	public void visit(Condition condition) {
		// da su bili tacni pokupio bi ih neki CondTerm
		Code.putJump(0); // -> StmtElse
		skipThen.push(Code.pc - 2);
		// | StmtThen
		// V
		while(!skipCondition.empty())
			Code.fixup(skipCondition.pop());
	}
	
	@Override
	public void visit(StatementThen statementThen) {
		Statement_if s = (Statement_if) statementThen.getParent();
		if (s.getElse() instanceof Else_yes) {
			Code.putJump(0); // -> if_end
			skipElse.push(Code.pc - 2);
		}
		Code.fixup(skipThen.pop());
	}
	
	@Override
	public void visit(StatementElse statementElse) {
		// samo se sa kraja StmtThen-a skace ovde
		Code.fixup(skipElse.pop());
	}
	
	@Override
	public void visit(CondTrueExpr condTrueExpr) {
		Code.putJump(0); // -> if_end
		skipElse.push(Code.pc - 2);
		Code.fixup(skipThen.pop());
	}
	
	@Override
	public void visit(CondFalseExpr condFalseExpr) {
		// samo se sa kraja StmtThen-a skace ovde
		Code.fixup(skipElse.pop());
	}
	

	
	// For loop
	private int for_cond;	// ForCond start
	private Stack<Integer> endFor = new Stack<Integer>(), // to patch with for_end address
			stepFor = new Stack<Integer>(); // ForStep start
	
	private void visit_for_init() {
		endFor.push(-1); // granicnik
		this.for_cond = Code.pc;
	}
	@Override
	public void visit(ForInitStatement_ds forInitStatement_ds) {
		visit_for_init();
	}
	@Override
	public void visit(ForInitStatement_e forInitStatement_e) {
		visit_for_init();
	}
	
	private void visit_for_cond() {
		Code.putJump(0); // -> for_body
		stepFor.push(Code.pc);
		endFor.push(skipThen.pop());
	}
	@Override
	public void visit(ForCondition_cond forCondition_cond) {
		visit_for_cond();
	}
	@Override
	public void visit(ForCondition_e forCondition_e) {
		visit_for_cond();
	}
	
	private void visit_for_step() {
		Code.putJump(for_cond); // -> ForCond
		Code.fixup(stepFor.peek() - 2);
		sofBreak.push(0);
	}
	@Override
	public void visit(ForStepStatement_ds forStepStatement_ds) {
		visit_for_step();
	}
	@Override
	public void visit(ForStepStatement_e forStepStatement_e) {
		visit_for_step();
	}
	
	
	@Override
	public void visit(ForBodyStatement forBodyStatement) {
		int stepStart = stepFor.pop();
		Code.putJump(stepStart);

		while(!endFor.isEmpty() && endFor.peek() != -1) {
			Code.fixup(endFor.pop());
		}
		endFor.pop(); // izbaci granicnik
		
		sofBreak.pop();
	}
	
	@Override
	public void visit(Statement_continue statement_continue) {
		Code.putJump(stepFor.peek());
	}
	
	@Override
	public void visit(Statement_break statement_break) {
		if (sofBreak.peek() == 0) {
			Code.putJump(0);
			endFor.push(Code.pc - 2);
		} else if (sofBreak.peek() == 1) {
			Code.putJump(0);
			endSwitch.push(Code.pc-2);
		}

	}
	
	// Switch
	private Stack<Obj> switchTemp = new Stack<Obj>();
	private Stack<Integer> skipSwitchCase = new Stack<Integer> (),
			skipSwitchStmt = new Stack<Integer> (),
			endSwitch = new Stack<Integer> (),
			sofBreak = new Stack<Integer> (); // 0: for; 1: switch
	
	@Override 
	public void visit(SwitchExpr switchExpr) {
		Obj obj = new Obj(Obj.Var, "switchExpr", Tab.intType);
		switchTemp.push(obj);
		Code.store(obj);
		
		sofBreak.push(1);
	}
	
	@Override
	public void visit(CaseNum caseNum) {
		Code.load(switchTemp.peek());
		Code.loadConst(caseNum.getN1());
		Code.putFalseJump(Code.eq, 0); // -> to next case
		// | do the statement
		// V
		skipSwitchStmt.push(Code.pc - 2);
		if (!skipSwitchCase.isEmpty())
			Code.fixup(skipSwitchCase.pop());
	}
	
	@Override
	public void visit(CaseDecl caseDecl) {
		Code.putJump(0); // -> skip condition check & go straight to statements
		Code.fixup(skipSwitchStmt.pop());
		skipSwitchCase.push(Code.pc - 2);
	}
	
	@Override
	public void visit(Statement_switch statement_switch) {
		sofBreak.pop();
		switchTemp.pop();
		
		if (!skipSwitchCase.isEmpty())
			Code.fixup(skipSwitchCase.pop());
		while (!endSwitch.isEmpty())
			Code.fixup(endSwitch.pop());
	}
	
}