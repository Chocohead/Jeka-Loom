package com.chocohead.loom.util;

public interface MappingProcessor {
	void acceptClass(String srcName, String dstName);

	void acceptMethod(String srcClassName, String srcName, String desc, String dstClassName, String dstName);

	void acceptMethodArg(String className, String methodName, String methodDesc, String dstClassName, int argIndex, int lvtIndex, String argName);

	void acceptMethodVar(String className, String methodName, String methodDesc, String dstClassName, int varIndex, int lvtIndex, String varName);

	void acceptField(String srcClassName, String srcName, String desc, String dstClassName, String dstName);
}