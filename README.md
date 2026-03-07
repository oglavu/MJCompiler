# MJCompiler

A compiler for the MicroJava programming language, developed as part of the Compiler Construction course.

The compiler translates MicroJava source programs into bytecode that executes on the MicroJava Virtual Machine.

The project implements the complete compilation pipeline:
* lexical analysis
* syntax analysis
* semantic analysis
* bytecode generation

## Table of contents

- [MJCompiler](#mjcompiler)
  - [Table of contents](#table-of-contents)
  - [Overview](#overview)
  - [Main components](#main-components)
  - [Prerequisites](#prerequisites)
  - [Build](#build)
  - [Usage](#usage)
  - [Language Features](#language-features)

## Overview

MicroJava is a simplified Java-like programming language designed for teaching compiler construction. The compiler processes a `.mj` source file and produces a `.obj` bytecode file that can be executed on the MicroJava Virtual Machine.

To see about the features of the MicroJava jump to [Language Features](#language-features) section.

## Main components
**Lexer**

- Generated from `src/spec/mjlexer.flex` spec
- Implemented using JFlex
- Responsible for tokenizing the source program

**Parser**

- Generated from `src/spec/mjparser.cup` spec
- Uses Java CUP (LALR parser generator)
- Builds the Abstract Syntax Tree (AST)

**Semantic analyzer**

- Traverses the AST
- Performs type checking
- Maintains the symbol table
- Reports semantic errors

**Code generator**

- Generates MicroJava bytecode
- Produces .obj executable files
 
Test samples are provided in the `test/` directory. Standard input is set to `test/input.txt` file due to Eclipse buggy behavior :P

## Prerequisites

To build and run the compiler you need JDK 8+, Apache Ant build tool and precompiled jar libraries that can be found in the `lib/` folder.

## Build

Navigate to the project root and perform:

```bash
ant -f build.xml compile
```
Target `compile` has dependencies on all required generated components (Lexer & Parser).

## Usage

Compile a MicroJava program:
```bash
java -cp "lib/*;src;config" rs.ac.bg.etf.pp1.Compiler test/program.mj
```

Run compiled program:
```bash
ant -f build.xml runObj -Dexe=test/program.obj
```
It is possible to run the program in debug mode using target `debug` and to see the disassembled code with `disasm`.

Default standard input is the `test/input.txt` file, but it can be set with `-Dinput=<input_path>` for `runObj` and `debug` targets.

## Language Features

The compiler supports the following language constructs:
* variables, arrays, enums
* arithmetic, logic and relational expressions
* control flow (if, for, switch)
* functions
* classes and inheritance
* virtual method calls
* polymorphism
