package org.meerkat.tmp

import org.meerkat.sppf.NonPackedNode
import org.meerkat.util.Input
import org.meerkat.sppf.SPPFLookup
import scala.reflect.ClassTag
import org.meerkat.sppf.DefaultSPPFLookup
import org.meerkat.sppf.Slot
import org.meerkat.tree.RuleType
import scala.util.matching.Regex
import scala.collection.mutable._
import scala.collection.JavaConversions._

object Parsers { import AbstractCPSParsers._
  
  implicit def obj1[ValA,ValB](implicit vals: ValA|~|ValB) = new CanBuildSequence[NonPackedNode,NonPackedNode,ValA,ValB] {
    implicit val m1 = obj4; implicit val m2 = obj4
    
    type T = NonPackedNode; type V = vals.R
      
    type Sequence = Parsers.Sequence
    def sequence(p: AbstractSequence[NonPackedNode]): Sequence 
      = new Sequence { 
          def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = p(input,i,sppfLookup)
          def size = p.size; def symbol = p.symbol; def ruleType = p.ruleType
        }  
    def index(a: T): Int = a.rightExtent
    def intermediate(a: T, b: T, p: Slot, sppfLookup: SPPFLookup): T = sppfLookup.getIntermediateNode(p, a, b)
      
    type SequenceBuilder = Parsers.SequenceBuilder { type Value = V }
    def builderSeq(f: Slot => Sequence) = new Parsers.SequenceBuilder { type Value = V; def apply(slot: Slot) = f(slot) }
  }
  
  implicit object obj2 extends CanBuildAlternative[NonPackedNode] {
    implicit val m = obj4
    def result(e: NonPackedNode, p: Slot, nt: Head, sppfLookup: SPPFLookup) = sppfLookup.getNonterminalNode(nt, p, e)
  }
  
  implicit def obj3[ValA,ValB] = new CanBuildAlternation[NonPackedNode,NonPackedNode,ValA,ValB] {
    implicit val m1 = obj4; implicit val m2 = obj4
    implicit val o1 = obj2; implicit val o2 = obj2
    
    type Alternation = Parsers.Alternation
    def alternation(p: AbstractParser[NonPackedNode]): Alternation
      = new Alternation {
          def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = p(input, i, sppfLookup)
          def symbol = p.symbol.asInstanceOf[org.meerkat.tree.Alt]
        }   
    type AlternationBuilder = Parsers.AlternationBuilder { type Value = ValB }
    def builderAlt(f: Head => Alternation) = new Parsers.AlternationBuilder { type Value = ValB; def apply(head: Head) = f(head) }
  }
  
  implicit object obj4 extends Memoizable[NonPackedNode] {
    type U = Int
    def value(t: NonPackedNode): Int = t.rightExtent
  }
  
  implicit def obj5[Val] = new CanBuildNonterminal[NonPackedNode,Val] {
    implicit val m = obj4
    
    type Nonterminal = Parsers.AbstractNonterminal { type Value = Val }
    def nonterminal(nt: String, p: AbstractParser[NonPackedNode]) 
      = new Parsers.AbstractNonterminal {
          type Value = Val
          def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = p(input, i, sppfLookup)
          def symbol = org.meerkat.tree.Nonterminal(nt)
          def name = nt
          override def toString = name
        }
    
    type Symbol = Parsers.Symbol { type Value = Val}
    def symbol(p: AbstractSymbol[NonPackedNode]) = new Parsers.Symbol { 
      def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = p(input,i,sppfLookup)
      def name = p.name; def symbol = p.symbol
      type Value = Val
    }
  }
  
  implicit def obj6[Val] = new CanBuildEBNF[NonPackedNode,Val] {
    implicit val m = obj4
    
    type T = NonPackedNode
    type Regular = AbstractNonterminal { type Value = Val }
    type Group = AbstractNonterminal { type Value = Val }
    
    def regular(sym: org.meerkat.tree.Nonterminal, p: AbstractParser[NonPackedNode]): Regular 
      = new AbstractNonterminal {
          def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = p(input, i, sppfLookup)
          def name = symbol.toString; def symbol = sym
          override def toString = name   
          type Value = Val
        }
    def group(p: AbstractParser[NonPackedNode]): Group 
      = new AbstractNonterminal {
          def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = p(input, i, sppfLookup)
          def name = symbol.toString; def symbol = org.meerkat.tree.Group(p.symbol)
          override def toString = name   
          type Value = Val
        }     
  }
  
  trait Sequence extends AbstractParser[NonPackedNode] with Slot { def size: Int; def symbol: org.meerkat.tree.Sequence }
  trait Alternation extends AbstractParser[NonPackedNode] { def symbol: org.meerkat.tree.Alt }
  
  trait Terminal extends Symbol { type Value = NoValue; def symbol: org.meerkat.tree.Terminal }
  
  trait AbstractNonterminal extends Symbol { def symbol: org.meerkat.tree.Nonterminal; type Abstract[X] = AbstractNonterminal { type Value = X } }
  
  type Nonterminal = AbstractNonterminal { type Value = NoValue }
  type &[A <: { type Abstract[_] },T] = A#Abstract[T]
  
  val Ø = new Terminal {
    def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = CPSResult.success(sppfLookup.getEpsilonNode(i))
    def symbol = org.meerkat.tree.Terminal(name)
    def name = "epsilon"; override def toString = name
  }
  
  trait SequenceBuilder extends (Slot => Sequence) with SequenceBuilderOps { import AbstractParser._ 
    type Value
    def action: Option[Any => Any] = None
    
    def ~ (p: Symbol)(implicit tuple: this.Value|~|p.Value, layout: Layout) = (this ~~ layout.get).~~(p)(tuple)
    def ~~ (p: Symbol)(implicit tuple: this.Value|~|p.Value) = { implicit val o = obj1(tuple); seq(this, p) }
    
    def & [V](f: this.Value => V) = new SequenceBuilderWithAction {
      type Value = V
      def apply(slot: Slot) = SequenceBuilder.this(slot)
      def action = Option({ x => f(x.asInstanceOf[SequenceBuilder.this.Value]) })
    }
    
    def ^[V](f: String => V)(implicit sub: this.Value <:< NoValue) = new SequenceBuilderWithAction {
      type Value = V
      def apply(slot: Slot) = SequenceBuilder.this(slot)
      def action = Option({ x => f(x.asInstanceOf[String]) })
    }
    
    def !(implicit ebnf: EBNF[this.Value]): AbstractNonterminal { type Value = ebnf.Group } 
      = groupSeq[NonPackedNode,ebnf.Group](this)
  }
  
  trait SequenceBuilderWithAction extends (Slot => Sequence) with SequenceBuilderOps { import AbstractParser._
    type Value
    def action: Option[Any => Any]
  }
  
  trait SequenceBuilderOps extends (Slot => Sequence) { import AbstractParser._
    type Value
    def action: Option[Any => Any]
    
    def | [U >: this.Value](p: AlternationBuilder { type Value = U }) = altSeqAlt(this, p)
    def | [U >: this.Value](p: SequenceBuilder { type Value = U }) = altSeq(this, p)
    def | [U >: this.Value](p: Symbol { type Value = U }) = altSeqSym(this, p)
    
    def | [U >: this.Value](q: SequenceBuilderWithAction { type Value = U }) = altSeq(this, q)
    def | [U >: this.Value](q: SymbolWithAction { type Value = U }) = altSeqSym(this, q)
  }
  
  trait AlternationBuilder extends (Head => Alternation) { import AbstractParser._
    type Value
    
    def | [U >: this.Value](p: AlternationBuilder { type Value = U }) = altAlt(this, p)
    def | [U >: this.Value](p: SequenceBuilder { type Value = U }) = altAltSeq(this, p)
    def | [U >: this.Value](p: Symbol { type Value = U }) = altAltSym(this, p)
    
    def | [U >: this.Value](q: SequenceBuilderWithAction)(implicit sub: this.Value <:< q.Value) = altAltSeq(this, q)
    def | [U >: this.Value](q: SymbolWithAction)(implicit sub: this.Value <:< q.Value) = altAltSym(this, q)
    
    def !(implicit ebnf: EBNF[this.Value]): AbstractNonterminal { type Value = ebnf.Group } 
      = groupAlt[NonPackedNode,ebnf.Group](this)
  }
  
  trait Symbol extends AbstractParser[NonPackedNode] with SymbolOps with SemanticActions with EBNFs with CharLevelDisambiguation { import AbstractParser._    
    type Value  
    def name: String
    def action: Option[Any => Any] = None
    
    def ~ (p: Symbol)(implicit tuple: this.Value |~| p.Value, layout: Layout) = (this ~~ layout.get).~~(p)(tuple)
    def ~~ (p: Symbol)(implicit tuple: this.Value|~|p.Value) = { implicit val o = obj1(tuple); seq(this, p) }    
  }
  
  trait SymbolWithAction extends AbstractParser[NonPackedNode] with SymbolOps { import AbstractParser._
    type Value  
    def name: String
    def action: Option[Any => Any]  
  }
  
  trait SymbolOps extends AbstractParser[NonPackedNode] { import AbstractParser._
    type Value  
    def name: String
    def action: Option[Any => Any]
  
    def | [U >: this.Value](p: AlternationBuilder { type Value = U }) = altSymAlt(this, p)
    def | [U >: this.Value](p: SequenceBuilder { type Value = U }) = altSymSeq(this, p)
    def | [U >: this.Value](p: Symbol { type Value = U }) = altSym(this, p)
    
    def | [U >: this.Value](q: SequenceBuilderWithAction { type Value = U }) = altSymSeq(this, q)
    def | [U >: this.Value](q: SymbolWithAction { type Value = U }) = altSym(this, q)
  }
  
  implicit def toTerminal(s: String) = new Terminal { 
    def apply(input: Input, i: Int, sppfLookup: SPPFLookup) 
      = if (input.startsWith(s, i)) { CPSResult.success(sppfLookup.getTerminalNode(s, i, i + s.length())) } 
        else CPSResult.failure
    def symbol = org.meerkat.tree.Terminal(s); def name = s; override def toString = name
  }
  
  implicit def toTerminal(r: Regex) = new Terminal {
    def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = { 
      val end = input.matchRegex(r, i)
      if(end != -1) CPSResult.success(sppfLookup.getTerminalNode(r.toString, i, end))
      else CPSResult.failure 
    }
    def name = r.toString; def symbol = org.meerkat.tree.Terminal(name)
  }
  
  def ntAlt[T](name: String, p: => AlternationBuilder { type Value = T }) = nonterminalAlt[NonPackedNode,T](name, p)  
  def ntSeq[T](name: String, p: => SequenceBuilder { type Value = T }) = nonterminalSeq[NonPackedNode,T](name, p)
  def ntSym(name: String, p: AbstractSymbol[NonPackedNode]) = nonterminalSym(name, p)
  
  trait SemanticActions { self: Symbol =>   
    def &[V](f: this.Value => V) = new SymbolWithAction {
      type Value = V
      def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = self(input, i, sppfLookup)
      def name = self.name; def symbol = self.symbol
      def action = Option({ x => f(x.asInstanceOf[self.Value]) })
    }   
    def ^[V](f: String => V)(implicit sub: this.Value <:< NoValue) = new SymbolWithAction {
      type Value = V
      def apply(input: Input, i: Int, sppfLookup: SPPFLookup) = self(input, i, sppfLookup)
      def name = self.name; def symbol = self.symbol
      def action = Option({ x => f(x.asInstanceOf[String]) })
    }
  }

  trait EBNFs { self: Symbol =>
    
    var opt: Option[AbstractNonterminal] = None
    def ?(implicit ebnf: EBNF[this.Value]): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      opt.asInstanceOf[Option[T]].getOrElse({
        val p = regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Opt(this.symbol), this & ebnf.unit | Ø ^ ebnf.empty) 
        opt = Option(p); p })
    }
    
    var star: Option[AbstractNonterminal] = None
    def *(implicit ebnf: EBNF[this.Value], layout: Layout): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      star.asInstanceOf[Option[T]].getOrElse({
        val p = regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Star(this.symbol), star.asInstanceOf[Option[T]].get ~ this & ebnf.add | Ø ^ ebnf.empty)
        star = Option(p); p })
    }
    
    var starstar: Option[AbstractNonterminal] = None
    def **(implicit ebnf: EBNF[this.Value]): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      starstar.asInstanceOf[Option[T]].getOrElse({
        val p = regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Star(this.symbol), starstar.asInstanceOf[Option[T]].get ~~ this & ebnf.add | Ø ^ ebnf.empty)
        starstar = Option(p); p })
    }
    
    var star_sep: Map[String,AbstractNonterminal] = new java.util.HashMap[String,AbstractNonterminal]()
    def *(sep: Terminal)(implicit ebnf: EBNF[this.Value], layout: Layout): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      star_sep.getOrElseUpdate(sep.name, {
        regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Star(this.symbol), star_sep.get(sep.name).asInstanceOf[T] ~ sep ~ this & ebnf.add | Ø ^ ebnf.empty)
      }).asInstanceOf[T]
    }
          
    var plus: Option[AbstractNonterminal] = None
    def +(implicit ebnf: EBNF[this.Value], layout: Layout): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      plus.asInstanceOf[Option[T]].getOrElse({ 
        val p = regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Plus(this.symbol), plus.asInstanceOf[Option[T]].get ~ this & ebnf.add | this & ebnf.unit)
        plus = Option(p); p })
    }
    
    var plusplus: Option[AbstractNonterminal] = None
    def ++(implicit ebnf: EBNF[this.Value]): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      plusplus.asInstanceOf[Option[T]].getOrElse({ 
        val p = regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Plus(this.symbol), plusplus.asInstanceOf[Option[T]].get ~~ this & ebnf.add | this & ebnf.unit)
        plusplus = Option(p); p })
    }
    
    var plus_sep: Map[String,AbstractNonterminal] = new java.util.HashMap[String,AbstractNonterminal]()
    def +(sep: Terminal)(implicit ebnf: EBNF[this.Value], layout: Layout): AbstractNonterminal { type Value = ebnf.OptOrSeq } = {
      type T = AbstractNonterminal { type Value = ebnf.OptOrSeq }
      plus_sep.getOrElseUpdate(sep.name, {
        regular[NonPackedNode,ebnf.OptOrSeq](org.meerkat.tree.Star(this.symbol), plus_sep.get(sep.name).asInstanceOf[T] ~ sep ~ this & ebnf.add | this & ebnf.unit)
      }).asInstanceOf[T]
    }
  }
  
  trait CharLevelDisambiguation { self: Symbol =>
    def \(arg: String) = postFilter[NonPackedNode](this, (input,t) => arg != input.substring(t.leftExtent, t.rightExtent), s" \\ $arg")
    def !>>(arg: String) = postFilter[NonPackedNode](this, (input,t) => !input.startsWith(arg, t.rightExtent), s" !>> $arg")
    def !<<(arg: String) = preFilter[NonPackedNode](this, (input,i) => !input.substring(0,i).endsWith(arg), s"$arg !<< ")
  }
}