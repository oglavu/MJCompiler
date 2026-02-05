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
		{	// chr
			Tab.chrObj.setAdr(Code.pc);
			Code.put(Code.enter);
			Code.put(1);
			Code.put(1);
			Code.put(Code.load_n);
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
		{	// ord
			Tab.ordObj.setAdr(Code.pc);
			Code.put(Code.enter);
			Code.put(1);
			Code.put(1);
			Code.put(Code.load_n);
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
		{	// len
			Tab.lenObj.setAdr(Code.pc);
			Code.put(Code.enter);
			Code.put(1);
			Code.put(1);
			Code.put(Code.load_n);
			Code.put(Code.arraylength);
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
		
	}
	
	@Override
	public void visit(MethodName methodName) {
		if (methodName.getI1().equals("main")) {
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
	public void visit(Designator_simple d) {
		SyntaxNode parent = d.getParent();
		if (parent.getClass() != DesignatorStatement_assign.class
				// e.g. arr = new int[10]; Sluzi da se levi arr ne pushuje
				&& parent.getClass() != Statement_read.class
				
				&& parent.getClass() != FactorSub_var.class
				// e.g. x = a; a bi se pushovao i kao designator i kao factor
				&& parent.getClass() != Designator_dot.class
				// e.g. x = EnumName.ELEM; pokusao bi load od EnumName
				&& d.obj.getKind() != Obj.Meth
				
		){
			Code.load(d.obj);	
		}
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
	public void visit(FactorSub_var f) {
		Code.load(f.getDesignator().obj);
	}
	
	@Override
	public void visit(Factor factor) {
		if(factor.getUnary() instanceof Unary_m) {
			Code.put(Code.neg);
		}
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
		Code.store(stmt.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_meth stmt) {
		int offset = stmt.getMethodInvokeName().obj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
		
		if(stmt.getMethodInvokeName().obj.getType() != Tab.noType)
			Code.put(Code.pop);
	}
	
	// Condition
	private Stack<Integer> ternaryCondFalse = new Stack<Integer>();
	private Stack<Integer> ternaryEnd = new Stack<Integer>();
	
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
	public void visit(CondFactNoRelop c) {
		Code.loadConst(1);
		// to be filled with corresponding ternaryCondFalse value
		Code.putFalseJump(Code.eq, 0); // -> false
		// | true
		// V
		ternaryCondFalse.push(Code.pc - 2); // adr before jmp
	}
	
	@Override
	public void visit(CondFactRelop c) {
		// to be filled with corresponding ternaryCondFalse value
		Code.putFalseJump(getRelopCode(c.getRelop()), 0); // -> false
		// | true
		// V
		ternaryCondFalse.push(Code.pc - 2); // adr before jmp
	}
	
	@Override
	public void visit(CondTrueExpr e) {
		// to be filled with corresponding ternaryEnd value
		Code.putJump(0); // -> end of ternary
		// | condFalseExpr code
		// V
		Code.fixup(ternaryCondFalse.pop());
		ternaryEnd.push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondFalseExpr e) {
		Code.fixup(ternaryEnd.pop());
	}
	
	@Override
	public void visit(StatementThen s) {
		Statement_if parent = (Statement_if) s.getParent();
		if (parent.getElse() instanceof Else_yes) {
			// to be filled with corresponding ternaryEnd value
			Code.putJump(0); // -> end of ternary
			// | condFalseExpr code
			// V
			Code.fixup(ternaryCondFalse.pop());
			ternaryEnd.push(Code.pc - 2);
		} else {
			// no else statement
			Code.fixup(ternaryCondFalse.pop());
		}

	}
	
	@Override
	public void visit(StatementElse e) {
		Code.fixup(ternaryEnd.pop());
	}
	
	// For loop
	private int for_cond;	// ForCond start
	private Stack<Integer> endFor = new Stack<Integer>(), // to patch with for_end address
			stepFor = new Stack<Integer>(); // ForStep start
	
	@Override
	public void visit(ForInitStatement forInitStatement) {
		endFor.push(-1); // granicnik
		this.for_cond = Code.pc;
	}
	
	@Override
	public void visit(ForCondNoRelop forCondNoRelop) {
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0); // -> for_end
		Code.putJump(0); // -> for_body
		
		stepFor.push(Code.pc);
		endFor.push(Code.pc - 5);
	}
	
	@Override
	public void visit(ForCondRelop forCond) {
		Code.putFalseJump(this.getRelopCode(forCond.getRelop()), 0); // -> for_end
		Code.putJump(0); // -> for_body
		
		stepFor.push(Code.pc);
		endFor.push(Code.pc - 5);
	}
	
	@Override
	public void visit(ForStepStatement forStepStatement) {
		Code.putJump(for_cond); // -> ForCond
		Code.fixup(stepFor.peek() - 2);
	}
	
	@Override
	public void visit(ForBodyStatement forBodyStatement) {
		int stepStart = stepFor.pop();
		Code.putJump(stepStart);

		while(!endFor.isEmpty() && endFor.peek() != -1) {
			Code.fixup(endFor.pop());
		}
		endFor.pop(); // izbaci granicnik
	}
	
	@Override
	public void visit(Statement_continue statement_continue) {
		Code.putJump(stepFor.peek());
	}
	
	@Override
	public void visit(Statement_break statement_break) {
		Code.putJump(0);
		endFor.push(Code.pc - 2);
	}
	
}