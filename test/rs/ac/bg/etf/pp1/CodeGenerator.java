package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.Stack;

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
	public void visit(MainName mainName) {
		
		mainPc = Code.pc;
		mainName.obj.setAdr(Code.pc);
		
		// Collect arguments and local variables.
		SyntaxNode methodNode = mainName.getParent();
		VarCounter varCnt = new VarCounter();
		methodNode.traverseTopDown(varCnt);
		
		// Generate the entry.
		Code.put(Code.enter);
		Code.put(0); // no formal params in lvl A main
		Code.put(varCnt.getCount());
	}
	
	@Override
	public void visit(MainMethod mainMethod) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(Designator_simple d) {
		SyntaxNode parent = d.getParent();
		if (parent.getClass() != DesignatorStatement_assign.class
				// e.g. arr = new int[10]; Sluzi da se levi arr ne pushuje
				&& parent.getClass() != FactorSub_var.class
				// e.g. x = a; a bi se pushovao i kao designator i kao factor
		){
			Code.load(d.obj);	
		}
	}
	
	@Override
	public void visit(Designator_dot d) {
		Code.loadConst(d.obj.getAdr());	// enum
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
		Code.putJump(0);
		Code.fixup(ternaryCondFalse.pop());
		ternaryEnd.push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondFalseExpr e) {
		Code.fixup(ternaryEnd.pop());
	}
	
}