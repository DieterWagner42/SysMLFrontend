I-Logix-RPY-Archive version 8.1.4 C++ 998726
{ IClass 
	- _ownerHandle = { IHandle 
		- _m2Class = "ISubsystem";
		- _filename = "AlarmPkg.sbs";
		- _subsystem = "";
		- _class = "";
		- _name = "AlarmPkg";
		- _id = OLDID 1424988 1;
	}
	- _id = OLDID 1424988 441;
	- _myState = 65536;
	- _properties = { IPropertyContainer 
		- Subjects = { IRPYRawContainer 
			- size = 2;
			- value = 
			{ IPropertySubject 
				- _Name = "CG";
				- Metaclasses = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IPropertyMetaclass 
						- _Name = "Package";
						- Properties = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IProperty 
								- _Name = "GenerateWithAggregates";
								- _Value = "True";
								- _Type = Bool;
							}
						}
					}
				}
			}
			{ IPropertySubject 
				- _Name = "CPP_CG";
				- Metaclasses = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IPropertyMetaclass 
						- _Name = "Class";
						- Properties = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IProperty 
								- _Name = "Embeddable";
								- _Value = "True";
								- _Type = Bool;
							}
						}
					}
				}
			}
		}
	}
	- _name = "AlarmController";
	- Stereotypes = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IHandle 
			- _m2Class = "IStereotype";
			- _filename = "AlarmPkg.sbs";
			- _subsystem = "AlarmPkg";
			- _class = "";
			- _name = "Facade";
			- _id = GUID 9b3abbb6-1505-11d4-9312-0010a4f1d0f6;
		}
	}
	- Annotations = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IRequirement 
			- _id = GUID 7f968f64-a3fd-48ca-b612-fe576275535f;
			- _name = "Indicators";
			- _description = { IDescription 
				- _text = "The alarm will have two LED's, one green and one red. 
The red led will indicate the alarm is active, 
the green will indicate that the alarm is powered up.";
			}
			- _body = "";
		}
	}
	- _theMainDiagram = { IHandle 
		- _m2Class = "IDiagram";
		- _filename = "home_alarm_overview.omd";
		- _subsystem = "AlarmPkg";
		- _class = "";
		- _name = "home alarm overview";
		- _id = OLDID 16900541 1;
	}
	- _description = { IDescription 
		- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Class that controls the alarm system\\f1\\rtlch\\par
}
";
	}
	- _lastID = 24;
	- weakCGTime = 1.9.2005::13:55:19;
	- strongCGTime = 11.23.2003::14:32:16;
	- Ports = { IRPYRawContainer 
		- size = 2;
		- value = 
		{ IPort 
			- _id = GUID ba9369fa-023b-4cfb-adc9-e922af45a638;
			- _name = "hwCtrl";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _id = GUID 380fadb4-48f9-4ae0-b6e7-e3f9a327091c;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
			- _implicitClass = { IClass 
				- _id = GUID 380fadb4-48f9-4ae0-b6e7-e3f9a327091c;
				- _myState = 8192;
				- _name = "hwCtrl_implicitContract";
				- Dependencies = { IRPYRawContainer 
					- size = 3;
					- value = 
					{ IDependency 
						- _id = GUID 67b458e5-3b72-4cb8-b69b-b561434eb541;
						- _name = "ILedCtrl";
						- Stereotypes = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IHandle 
								- _m2Class = "IStereotype";
								- _filename = "PredefinedTypes.sbs";
								- _subsystem = "PredefinedTypes";
								- _class = "";
								- _name = "Usage";
								- _id = GUID 42aa4325-6112-4e88-87ce-66008878f1db;
							}
						}
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "ILedCtrl";
							- _id = GUID e55ab0dd-a2eb-4a76-bc70-c9a9afd95b97;
						}
					}
					{ IDependency 
						- _id = GUID 5d30c652-5766-4051-b5e2-7be094df647e;
						- _name = "ILightCtrl";
						- Stereotypes = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IHandle 
								- _m2Class = "IStereotype";
								- _filename = "PredefinedTypes.sbs";
								- _subsystem = "PredefinedTypes";
								- _class = "";
								- _name = "Usage";
								- _id = GUID 42aa4325-6112-4e88-87ce-66008878f1db;
							}
						}
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "ILightCtrl";
							- _id = GUID 1df77aff-fcf1-495b-815a-23e9348577e9;
						}
					}
					{ IDependency 
						- _id = GUID ee56bd22-d638-4f38-b8e4-fa124a9cdbe7;
						- _name = "ISirenCtrl";
						- Stereotypes = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IHandle 
								- _m2Class = "IStereotype";
								- _filename = "PredefinedTypes.sbs";
								- _subsystem = "PredefinedTypes";
								- _class = "";
								- _name = "Usage";
								- _id = GUID 42aa4325-6112-4e88-87ce-66008878f1db;
							}
						}
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "ISirenCtrl";
							- _id = GUID 9b3268e8-cd8a-42da-b528-175797358715;
						}
					}
				}
				- Stereotypes = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IHandle 
						- _m2Class = "IStereotype";
						- _filename = "PredefinedTypes.sbs";
						- _subsystem = "PredefinedTypes";
						- _class = "";
						- _name = "Interface";
						- _id = GUID 2a7b8a63-f54f-4379-bab0-02800a5084e1;
					}
				}
				- weakCGTime = 7.10.2003::11:28:18;
				- strongCGTime = 7.10.2003::11:28:18;
				- _multiplicity = "";
				- _itsStateChart = { IHandle 
					- _m2Class = "";
				}
				- _classModifier = Unspecified;
			}
		}
		{ IPort 
			- _id = GUID 0079851e-40a8-42e7-837d-a959abfa8ebc;
			- _name = "hwObserver";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _id = GUID 8ef8f4a7-0dfa-431a-88d2-42b13463d1aa;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
			- _implicitClass = { IClass 
				- _id = GUID 8ef8f4a7-0dfa-431a-88d2-42b13463d1aa;
				- _myState = 8192;
				- _name = "hwObserver_implicitContract";
				- Stereotypes = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IHandle 
						- _m2Class = "IStereotype";
						- _filename = "PredefinedTypes.sbs";
						- _subsystem = "PredefinedTypes";
						- _class = "";
						- _name = "Interface";
						- _id = GUID 2a7b8a63-f54f-4379-bab0-02800a5084e1;
					}
				}
				- weakCGTime = 7.10.2003::11:29:40;
				- strongCGTime = 7.10.2003::11:29:19;
				- Inheritances = { IRPYRawContainer 
					- size = 3;
					- value = 
					{ IGeneralization 
						- _id = GUID 77853410-c275-46a1-a0c6-5ce532589e3f;
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "IDoorListener";
							- _id = GUID e65ab3c7-2dfd-44cd-93ca-b895d7f57007;
						}
						- _inheritanceType = iPublic;
						- _isVirtual = 0;
					}
					{ IGeneralization 
						- _id = GUID c6ee7b65-ea96-4616-8bd1-cc8317919589;
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "IKeyListener";
							- _id = GUID db9992d8-3be9-4867-8871-bb48bc2d5baa;
						}
						- _inheritanceType = iPublic;
						- _isVirtual = 0;
					}
					{ IGeneralization 
						- _id = GUID a83d961c-fae9-49a2-b730-3eb2fb6e6e5f;
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "IMovementListener";
							- _id = GUID 065ba526-937e-4fd4-b77d-4e2049faad9c;
						}
						- _inheritanceType = iPublic;
						- _isVirtual = 0;
					}
				}
				- _multiplicity = "";
				- _itsStateChart = { IHandle 
					- _m2Class = "";
				}
				- _classModifier = Unspecified;
			}
			- _behavioral = 1;
		}
	}
	- Operations = { IRPYRawContainer 
		- size = 15;
		- value = 
		{ IPrimitiveOperation 
			- _id = GUID 3c06ac4a-2887-11d3-ac08-0010a4f1d0f6;
			- _properties = { IPropertyContainer 
				- Subjects = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IPropertySubject 
						- _Name = "CPP_CG";
						- Metaclasses = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IPropertyMetaclass 
								- _Name = "Operation";
								- Properties = { IRPYRawContainer 
									- size = 1;
									- value = 
									{ IProperty 
										- _Name = "Inline";
										- _Value = "in_source";
										- _Type = Enum;
										- _ExtraTypeInfo = "none,in_header,in_source";
									}
								}
							}
						}
					}
				}
			}
			- _name = "init";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 initialise the leds into a known state.\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iProtected;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "theGreenLed.GEN(evOn);
theRedLed.GEN(evOff);";
			}
		}
		{ IConstructor 
			- _id = GUID 3c06b931-2887-11d3-ac08-0010a4f1d0f6;
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Initialise the LED's\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "";
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "theRedLed.setIdent( RED );
theGreenLed.setIdent( GREEN );";
			}
			- _initializer = "";
		}
		{ IPrimitiveOperation 
			- _id = GUID ad1844f7-2898-11d3-ac09-0010a4f1d0f6;
			- _name = "flashGreenLed";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 flash Green led on and off 4 times\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "theGreenLed.GEN(evFlash(4));";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee3a1-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onArm";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Generates an event and sends it to itself\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "GEN(evArm);";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee4e7-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onDisarm";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Generates an event and sends it to itself\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "GEN(evDisarm);";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee582-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onDoor";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 routine called when a door opening has been detected\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "GEN(evDoor);";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee5fd-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onKey";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Acts as a facade and generates and sends an event to its keypad\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 1;
				- value = 
				{ IArgument 
					- _id = GUID c6eee5fe-cc43-11d3-ae1e-0010a4f1d0f6;
					- _name = "n";
					- _defaultValue = "";
					- _typeOf = { IHandle 
						- _m2Class = "IType";
						- _filename = "PredefinedTypesC++.sbs";
						- _subsystem = "PredefinedTypesCpp";
						- _class = "";
						- _name = "int";
						- _id = GUID 6024c6ab-fe9e-402e-954f-27aca6d3b5e4;
					}
					- _isOrdered = 0;
					- _argumentDirection = In;
				}
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "theKeypad.GEN(evKey(n));";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee6f9-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onKeyOff";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Acts as a facade and generates and sends an event to its keypad\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "theKeypad.GEN(evKeyOff);";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee765-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onKeyOn";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Acts as a facade and generates and sends an event to its keypad\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "theKeypad.GEN(evKeyOn);";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID c6eee7e4-cc43-11d3-ae1e-0010a4f1d0f6;
			- _name = "onMovement";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Generates an event and sends it to itself\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _returnType = { IHandle 
				- _m2Class = "IType";
				- _filename = "PredefinedTypesC++.sbs";
				- _subsystem = "PredefinedTypesCpp";
				- _class = "";
				- _name = "void";
				- _id = GUID c6b70728-b655-45a0-abb7-8ea5d3da94be;
			}
			- _abstract = 0;
			- _final = 0;
			- _concurrency = Sequential;
			- _protection = iPublic;
			- _static = 0;
			- _constant = 0;
			- _itsBody = { IBody 
				- _bodyData = "GEN(evMovement);";
			}
		}
		{ IReception 
			- _id = GUID 63f34d7c-3163-4d5d-8171-e1a7b0a8a723;
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _event = { IHandle 
				- _m2Class = "IEvent";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "evArm()";
				- _id = OLDID 1424988 314;
			}
		}
		{ IReception 
			- _id = GUID 7fc90883-711f-444c-81f0-6f829a52105f;
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _event = { IHandle 
				- _m2Class = "IEvent";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "evMovement()";
				- _id = OLDID 1424988 321;
			}
		}
		{ IReception 
			- _id = GUID 572911bb-6c33-45a1-9f8c-c59834833cee;
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _event = { IHandle 
				- _m2Class = "IEvent";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "evDoor()";
				- _id = OLDID 1424988 316;
			}
		}
		{ IReception 
			- _id = GUID ccb011ed-57fe-4022-9579-d1122ed057fc;
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _event = { IHandle 
				- _m2Class = "IEvent";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "evTemporise()";
				- _id = GUID be010079-3f3b-11d3-ac65-0010a4f1d0f6;
			}
		}
		{ IReception 
			- _id = GUID 46455302-547e-47f2-a4e0-02c33b185973;
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 0;
			}
			- _event = { IHandle 
				- _m2Class = "IEvent";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "evDisarm()";
				- _id = GUID be0101e7-3f3b-11d3-ac65-0010a4f1d0f6;
			}
		}
	}
	- Inheritances = { IRPYRawContainer 
		- size = 3;
		- value = 
		{ IGeneralization 
			- _id = GUID 29d5aaac-ec35-4f05-b4e0-76ad616f2a62;
			- _dependsOn = { INObjectHandle 
				- _m2Class = "IClass";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg::AbstractHW";
				- _class = "";
				- _name = "IDoorListener";
				- _id = GUID e65ab3c7-2dfd-44cd-93ca-b895d7f57007;
			}
			- _inheritanceType = iPublic;
			- _isVirtual = 0;
		}
		{ IGeneralization 
			- _id = GUID 5a55487c-9f6a-4a77-a08c-661f646eb5b6;
			- _dependsOn = { INObjectHandle 
				- _m2Class = "IClass";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg::AbstractHW";
				- _class = "";
				- _name = "IKeyListener";
				- _id = GUID db9992d8-3be9-4867-8871-bb48bc2d5baa;
			}
			- _inheritanceType = iPublic;
			- _isVirtual = 0;
		}
		{ IGeneralization 
			- _id = GUID 5307cc13-3ad8-473d-bab3-43484b755090;
			- _dependsOn = { INObjectHandle 
				- _m2Class = "IClass";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg::AbstractHW";
				- _class = "";
				- _name = "IMovementListener";
				- _id = GUID 065ba526-937e-4fd4-b77d-4e2049faad9c;
			}
			- _inheritanceType = iPublic;
			- _isVirtual = 0;
		}
	}
	- _multiplicity = "";
	- StateCharts = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IStateChart 
			- _id = OLDID 1424988 447;
			- _myState = 2048;
			- _properties = { IPropertyContainer 
				- Subjects = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IPropertySubject 
						- _Name = "Format";
						- Metaclasses = { IRPYRawContainer 
							- size = 3;
							- value = 
							{ IPropertyMetaclass 
								- _Name = "DefaultTransition";
								- Properties = { IRPYRawContainer 
									- size = 7;
									- value = 
									{ IProperty 
										- _Name = "Font.Font";
										- _Value = "Arial";
										- _Type = String;
									}
									{ IProperty 
										- _Name = "Font.FontColor";
										- _Value = "0,0,255";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Font.Italic";
										- _Value = "0";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Size";
										- _Value = "10";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Weight";
										- _Value = "400";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineColor";
										- _Value = "255,0,0";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Line.LineWidth";
										- _Value = "0";
										- _Type = Int;
									}
								}
							}
							{ IPropertyMetaclass 
								- _Name = "State";
								- Properties = { IRPYRawContainer 
									- size = 10;
									- value = 
									{ IProperty 
										- _Name = "Fill.FillColor";
										- _Value = "0,255,255";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Fill.Transparent_Fill";
										- _Value = "1";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Font";
										- _Value = "Arial";
										- _Type = String;
									}
									{ IProperty 
										- _Name = "Font.FontColor";
										- _Value = "0,0,0";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Font.Italic";
										- _Value = "0";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Size";
										- _Value = "10";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Weight";
										- _Value = "400";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineColor";
										- _Value = "0,128,64";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Line.LineStyle";
										- _Value = "0";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineWidth";
										- _Value = "1";
										- _Type = Int;
									}
								}
							}
							{ IPropertyMetaclass 
								- _Name = "Transition";
								- Properties = { IRPYRawContainer 
									- size = 7;
									- value = 
									{ IProperty 
										- _Name = "Font.Font";
										- _Value = "Arial";
										- _Type = String;
									}
									{ IProperty 
										- _Name = "Font.FontColor";
										- _Value = "0,0,255";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Font.Italic";
										- _Value = "0";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Size";
										- _Value = "10";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Font.Weight";
										- _Value = "400";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineColor";
										- _Value = "255,0,0";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Line.LineWidth";
										- _Value = "1";
										- _Type = Int;
									}
								}
							}
						}
					}
				}
			}
			- _name = "statechart_23";
			- _lastModifiedTime = "1.9.2005::13:56:6";
			- _graphicChart = { CGIStateChart 
				- _id = OLDID 1424988 511;
				- m_type = 4;
				- m_pModelObject = { IHandle 
					- _m2Class = "IStateChart";
					- _id = OLDID 1424988 447;
				}
				- m_pParent = ;
				- m_name = { CGIText 
					- m_str = "";
					- m_style = "Arial" 10 0 0 288 0 ;
					- m_color = { IColor 
						- m_fgColor = 0;
						- m_bgColor = 0;
						- m_bgFlag = 0;
					}
					- m_position = 1 0 0  ;
					- m_nIdent = 0;
					- m_bImplicitSetRectPoints = 0;
					- m_nOrientationCtrlPt = 8;
				}
				- m_drawBehavior = 3452816845;
				- m_bIsPreferencesInitialized = 1;
				- elementList = 22;
				{ CGIState 
					- _id = OLDID 1424988 528;
					- m_type = 5;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 448;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "ROOT";
						- m_style = "Arial" 10 0 0 1020 544 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 1 0 0  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 50 50  1046 50  1046 699  50 699  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = GUID b5dcd3d5-2874-11d3-ac08-0010a4f1d0f6;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = GUID b5dcd3d6-2874-11d3-ac08-0010a4f1d0f6;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 0 0 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 4 573 425  587 425  587 442  573 442  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 7;
						- m_nVerticalSpacing = -9;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 524;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 532;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "evMovement";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 593 413  690 413  690 432  593 432  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -10;
						- m_nVerticalSpacing = -35;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 1 588 415  ;
					- m_anglePoint1 = 0 -1 ;
					- m_anglePoint2 = 1 0 ;
					- m_line_style = 2;
					- m_SourcePort = 1097 912 ;
					- m_TargetPort = 295 21 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 512;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 494;
					}
					- m_pParent = OLDID 1424988 526;
					- m_name = { CGIText 
						- m_str = "exiting";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 19.6653876923188 19.2933946061026  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.185185 0 0 0.108051 547.166 106.806 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = OLDID 1424988 513;
					- m_type = 29;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 458;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 0 0 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 463 251  477 251  477 268  463 268  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 7;
						- m_nVerticalSpacing = -9;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 526;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 523;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 0 0 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 493 253  507 253  507 270  493 270  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nVerticalSpacing = -17;
						- m_nOrientationCtrlPt = 0;
					}
					- m_anglePoint1 = 0 -1 ;
					- m_anglePoint2 = 0 -1 ;
					- m_line_style = 1;
					- m_SourcePort = 156 265 ;
					- m_TargetPort = 85 9 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 514;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 500;
					}
					- m_pParent = OLDID 1424988 528;
					- m_name = { CGIText 
						- m_str = "off";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 -4.53820200254187 19.3924848771007  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.0748175 0 0 0.045283 201.15 188.592 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = OLDID 1424988 515;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 501;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 441 197  455 197  455 214  441 214  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 514;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 512;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "evTemporise";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 315 170  412 170  412 189  315 189  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -156;
						- m_nVerticalSpacing = -5;
						- m_nOrientationCtrlPt = 0;
					}
					- m_anglePoint1 = 0.999999606219346 -0.000887446422075348 ;
					- m_anglePoint2 = 0.999999606219346 -0.000887446422075348 ;
					- m_line_style = 0;
					- m_SourcePort = 1094 517 ;
					- m_TargetPort = -1 508 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 516;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 465;
					}
					- m_pParent = OLDID 1424988 523;
					- m_name = { CGIText 
						- m_str = "detecting";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 19.6351528421223 12.8124431529073  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.245496 0 0 0.160959 537.991 160.139 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 7;
					- m_nIsNameFormat = 7;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = OLDID 1424988 517;
					- m_type = 29;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 462;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 638 320  652 320  652 337  638 337  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 523;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 516;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 640 290  654 290  654 307  640 307  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -7;
						- m_nVerticalSpacing = -8;
						- m_nOrientationCtrlPt = 0;
					}
					- m_anglePoint1 = 1 0 ;
					- m_anglePoint2 = 1 0 ;
					- m_line_style = 1;
					- m_SourcePort = 445 245 ;
					- m_TargetPort = 1 516 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGITrans 
					- _id = OLDID 1424988 518;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 489;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 1020 544 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 778 442  792 442  792 459  778 459  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 520;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 516;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "tm(SILENCE_TIME)";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 761 378  903 378  903 397  761 397  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -12;
						- m_nVerticalSpacing = -42;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 2 828 435  760 435  ;
					- m_anglePoint1 = 0 1 ;
					- m_anglePoint2 = 0 1 ;
					- m_line_style = 2;
					- m_SourcePort = 537 21 ;
					- m_TargetPort = 986 1080 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGITrans 
					- _id = OLDID 1424988 519;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 495;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 0 0 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 689 233  703 233  703 250  689 250  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 7;
						- m_nVerticalSpacing = -9;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 512;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 516;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "tm(EXIT_TIME)";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 709 243  823 243  823 262  709 262  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -10;
						- m_nVerticalSpacing = -11;
						- m_nOrientationCtrlPt = 0;
					}
					- m_anglePoint1 = -0.0066026939130811 -0.999978201978968 ;
					- m_anglePoint2 = -0.0066026939130811 -0.999978201978968 ;
					- m_line_style = 0;
					- m_SourcePort = 506 1069 ;
					- m_TargetPort = 433 20 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 520;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 488;
					}
					- m_pParent = OLDID 1424988 523;
					- m_name = { CGIText 
						- m_str = "silence";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 25.9512982654872 2.27752839654871  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.216216 0 0 0.14726 832.306 768.264 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 49723908;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = OLDID 1424988 521;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 506;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 244 322  258 322  258 339  244 339  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 514;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 526;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "evArm";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 356 311  410 311  410 330  356 330  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 80;
						- m_nVerticalSpacing = -8;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 1 267 334  ;
					- m_anglePoint1 = 1 0 ;
					- m_anglePoint2 = 0 -1 ;
					- m_line_style = 2;
					- m_SourcePort = 880 1069 ;
					- m_TargetPort = -2 436 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGITrans 
					- _id = OLDID 1424988 522;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 471;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 697 433  711 433  711 450  697 450  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 516;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 532;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "evMovement";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 651 450  748 450  748 469  651 469  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -67;
						- m_nVerticalSpacing = 14;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 2 720 451  645 451  ;
					- m_anglePoint1 = 0 -1 ;
					- m_anglePoint2 = 0 -1 ;
					- m_line_style = 2;
					- m_SourcePort = 584 1080 ;
					- m_TargetPort = 939 21 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 523;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 461;
					}
					- m_pParent = OLDID 1424988 526;
					- m_name = { CGIText 
						- m_str = "active";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 10.6434391436012 12.731852573696  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.865497 0 0 0.618644 82.9162 311.085 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGIState 
					- _id = OLDID 1424988 524;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 476;
					}
					- m_pParent = OLDID 1424988 523;
					- m_name = { CGIText 
						- m_str = "entering";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 9.35352978099898 8.24667462902427  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.243243 0 0 0.150685 57.2613 358.027 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = OLDID 1424988 525;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 453;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 212 447  226 447  226 464  212 464  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 526;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 514;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "evDisarm";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 329 415  401 415  401 434  329 434  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 101;
						- m_nVerticalSpacing = -9;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 1 214 440  ;
					- m_anglePoint1 = 0 1 ;
					- m_anglePoint2 = -1 0 ;
					- m_line_style = 2;
					- m_SourcePort = -2 674 ;
					- m_TargetPort = 172 1069 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 526;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 452;
					}
					- m_pParent = OLDID 1424988 528;
					- m_name = { CGIText 
						- m_str = "armed";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 27.0859612290827 20.1240031899566  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.468066 0 0 0.445283 404.936 139.992 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 1077001558;
					- m_nIsNameFormat = 644157777;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				{ CGITrans 
					- _id = OLDID 1424988 527;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 466;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 660 403  674 403  674 420  660 420  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 516;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 524;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "evDoor";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 588 368  646 368  646 387  588 387  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -72;
						- m_nVerticalSpacing = -13;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 1 686 396  ;
					- m_anglePoint1 = -1 0 ;
					- m_anglePoint2 = 0 -1 ;
					- m_line_style = 2;
					- m_SourcePort = 242 1080 ;
					- m_TargetPort = 1097 454 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGITrans 
					- _id = OLDID 1424988 529;
					- m_type = 29;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 449;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 1020 544 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 219 142  233 142  233 159  219 159  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 528;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 514;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "/init();";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 248 137  298 137  298 156  248 156  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -8;
						- m_nVerticalSpacing = -15;
						- m_nOrientationCtrlPt = 0;
					}
					- m_anglePoint1 = 0 -1 ;
					- m_anglePoint2 = 0 -1 ;
					- m_line_style = 1;
					- m_SourcePort = 242 133 ;
					- m_TargetPort = 546 9 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGITrans 
					- _id = OLDID 1424988 530;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 477;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 1020 544 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 484 479  498 479  498 496  484 496  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 524;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 532;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "tm(ENTRY_TIME)";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 445 514  574 514  574 533  445 533  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -76;
						- m_nVerticalSpacing = 19;
						- m_nOrientationCtrlPt = 0;
					}
					- m_arrow = 1 506 511  ;
					- m_anglePoint1 = 1 0 ;
					- m_anglePoint2 = 0 -1 ;
					- m_line_style = 2;
					- m_SourcePort = 396 1081 ;
					- m_TargetPort = 1 465 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGITrans 
					- _id = OLDID 1424988 531;
					- m_type = 28;
					- m_pModelObject = { IHandle 
						- _m2Class = "ITransition";
						- _id = OLDID 1424988 483;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 35032536 34648128 ;
						- m_color = { IColor 
							- m_fgColor = 4;
							- m_bgColor = 4;
							- m_bgFlag = 0;
						}
						- m_position = 4 707 520  721 520  721 537  707 537  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_bIsPreferencesInitialized = 1;
					- m_pSource = OLDID 1424988 532;
					- m_sourceType = 'F';
					- m_pTarget = OLDID 1424988 520;
					- m_targetType = 'T';
					- m_direction = ' ';
					- m_rpn = { CGIText 
						- m_str = "tm(ALARM_TIME)";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 2;
							- m_bgColor = 2;
							- m_bgFlag = 0;
						}
						- m_position = 4 665 486  796 486  796 505  665 505  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = -55;
						- m_nVerticalSpacing = -12;
						- m_nOrientationCtrlPt = 0;
					}
					- m_anglePoint1 = 1 0 ;
					- m_anglePoint2 = 1 0 ;
					- m_line_style = 1;
					- m_SourcePort = 1097 514 ;
					- m_TargetPort = 1 563 ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_bShowName = 1;
				}
				{ CGIState 
					- _id = OLDID 1424988 532;
					- m_type = 8;
					- m_pModelObject = { IHandle 
						- _m2Class = "IState";
						- _id = OLDID 1424988 482;
					}
					- m_pParent = OLDID 1424988 523;
					- m_name = { CGIText 
						- m_str = "intrusion";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 14.003755221788 2.19884325075779  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 2054;
					- m_transform = 0.218468 0 0 0.14726 291.716 775.524 ;
					- m_bIsPreferencesInitialized = 1;
					- m_polygon = 4 -2 9  1094 9  1094 1069  -2 1069  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- nAndLineCount = 0;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_pDefaultComponentRelation = ;
				}
				
				- m_access = 'Z';
				- m_modified = 'N';
				- m_fileVersion = "";
				- m_nModifyDate = 1018;
				- m_nCreateDate = 544;
				- m_creator = "";
				- m_bScaleWithZoom = 0;
				- m_arrowStyle = 'S';
				- m_pRoot = OLDID 1424988 528;
				- m_currentLeftTop = -6.91489126281651 -3.37447925925183 ;
				- m_currentRightBottom = 32.345604654542 22.9307335513372 ;
				- m_pInheritsFrom = { IHandle 
					- _m2Class = "";
				}
			}
			- _defNumber = 2;
			- Transitions = { IRPYRawContainer 
				- size = 13;
				- value = 
				{ IDefaultDrvdTrans 
					- _id = OLDID 1424988 449;
					- _itsLabel = { ILabel 
						- _id = GUID 3c06ac00-2887-11d3-ac08-0010a4f1d0f6;
						- _itsTrigger = NULL;
						- _itsGuard = NULL;
						- _itsAction = { IAction 
							- _id = GUID 3c06ac02-2887-11d3-ac08-0010a4f1d0f6;
							- _body = "init();";
						}
					}
					- _itsTarget = OLDID 1424988 500;
					- _staticReaction = 0;
					- _itsSource = ;
					- _ofState = OLDID 1424988 448;
				}
				{ ITransition 
					- _id = OLDID 1424988 501;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 504;
						- _itsTrigger = { IInterfaceItemTrigger 
							- _id = OLDID 1424988 505;
							- _body = "evArm";
							- _info = "";
							- _itsInterfaceItem = { IHandle 
								- _m2Class = "IEvent";
								- _filename = "AlarmPkg.sbs";
								- _subsystem = "AlarmPkg";
								- _class = "";
								- _name = "evTemporise()";
								- _id = GUID be010079-3f3b-11d3-ac65-0010a4f1d0f6;
							}
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 494;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 500;
				}
				{ ITransition 
					- _id = OLDID 1424988 466;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 469;
						- _itsTrigger = { IInterfaceItemTrigger 
							- _id = OLDID 1424988 470;
							- _body = "evPir";
							- _info = "";
							- _itsInterfaceItem = { IHandle 
								- _m2Class = "IEvent";
								- _filename = "AlarmPkg.sbs";
								- _subsystem = "AlarmPkg";
								- _class = "";
								- _name = "evDoor()";
								- _id = OLDID 1424988 316;
							}
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 476;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 465;
				}
				{ ITransition 
					- _id = OLDID 1424988 477;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 480;
						- _itsTrigger = { ITimeout 
							- _id = OLDID 1424988 481;
							- _body = "tm(ENTRY_TIME)";
							- _info = "";
							- _time = "ENTRY_TIME";
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 482;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 476;
				}
				{ ITransition 
					- _id = OLDID 1424988 471;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 474;
						- _itsTrigger = { IInterfaceItemTrigger 
							- _id = OLDID 1424988 475;
							- _body = "evPir";
							- _info = "";
							- _itsInterfaceItem = { IHandle 
								- _m2Class = "IEvent";
								- _filename = "AlarmPkg.sbs";
								- _subsystem = "AlarmPkg";
								- _class = "";
								- _name = "evMovement()";
								- _id = OLDID 1424988 321;
							}
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 482;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 465;
				}
				{ ITransition 
					- _id = OLDID 1424988 489;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 492;
						- _itsTrigger = { ITimeout 
							- _id = OLDID 1424988 493;
							- _body = "tm(SILENCE_TIME)";
							- _info = "";
							- _time = "SILENCE_TIME";
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 465;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 488;
				}
				{ ITransition 
					- _id = OLDID 1424988 483;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 486;
						- _itsTrigger = { ITimeout 
							- _id = OLDID 1424988 487;
							- _body = "tm(ALARM_TIME)";
							- _info = "";
							- _time = "ALARM_TIME";
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 488;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 482;
				}
				{ ITransition 
					- _id = OLDID 1424988 453;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 456;
						- _itsTrigger = { IInterfaceItemTrigger 
							- _id = OLDID 1424988 457;
							- _body = "evDisarm";
							- _info = "";
							- _itsInterfaceItem = { IHandle 
								- _m2Class = "IEvent";
								- _filename = "AlarmPkg.sbs";
								- _subsystem = "AlarmPkg";
								- _class = "";
								- _name = "evDisarm()";
								- _id = GUID be0101e7-3f3b-11d3-ac65-0010a4f1d0f6;
							}
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 500;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 452;
				}
				{ IDefaultDrvdTrans 
					- _id = OLDID 1424988 462;
					- _itsLabel = NULL;
					- _itsTarget = OLDID 1424988 465;
					- _staticReaction = 0;
					- _itsSource = ;
					- _ofState = OLDID 1424988 461;
				}
				{ IDefaultDrvdTrans 
					- _id = OLDID 1424988 458;
					- _myState = 8192;
					- _itsLabel = NULL;
					- _itsTarget = OLDID 1424988 461;
					- _staticReaction = 0;
					- _itsSource = ;
					- _ofState = OLDID 1424988 452;
				}
				{ ITransition 
					- _id = OLDID 1424988 506;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 509;
						- _itsTrigger = { IInterfaceItemTrigger 
							- _id = OLDID 1424988 510;
							- _body = "evArm";
							- _info = "";
							- _itsInterfaceItem = { IHandle 
								- _m2Class = "IEvent";
								- _filename = "AlarmPkg.sbs";
								- _subsystem = "AlarmPkg";
								- _class = "";
								- _name = "evArm()";
								- _id = OLDID 1424988 314;
							}
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 452;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 500;
				}
				{ ITransition 
					- _id = OLDID 1424988 495;
					- _itsLabel = { ILabel 
						- _id = OLDID 1424988 498;
						- _itsTrigger = { ITimeout 
							- _id = OLDID 1424988 499;
							- _body = "tm(EXIT_TIME)";
							- _info = "";
							- _time = "EXIT_TIME";
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 465;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 494;
				}
				{ ITransition 
					- _id = GUID b5dcd3d6-2874-11d3-ac08-0010a4f1d0f6;
					- _myState = 2048;
					- _name = "transition_0";
					- _itsLabel = { ILabel 
						- _id = GUID b5dcd3d8-2874-11d3-ac08-0010a4f1d0f6;
						- _itsTrigger = { IInterfaceItemTrigger 
							- _id = GUID b5dcd3da-2874-11d3-ac08-0010a4f1d0f6;
							- _body = "evPir";
							- _info = "";
							- _itsInterfaceItem = { IHandle 
								- _m2Class = "IEvent";
								- _filename = "AlarmPkg.sbs";
								- _subsystem = "AlarmPkg";
								- _class = "";
								- _name = "evMovement()";
								- _id = OLDID 1424988 321;
							}
						}
						- _itsGuard = NULL;
						- _itsAction = NULL;
					}
					- _itsTarget = OLDID 1424988 482;
					- _staticReaction = 0;
					- _itsSource = OLDID 1424988 476;
				}
			}
			- States = { IRPYRawContainer 
				- size = 9;
				- value = 
				{ IState 
					- _id = OLDID 1424988 465;
					- _myState = 8192;
					- _name = "detecting";
					- _parent = OLDID 1424988 461;
					- _defaultTrans = ;
				}
				{ IState 
					- _id = OLDID 1424988 476;
					- _myState = 8192;
					- _name = "entering";
					- _parent = OLDID 1424988 461;
					- _defaultTrans = ;
					- _entryAction = { IAction 
						- _id = GUID 3c06c7f0-2887-11d3-ac08-0010a4f1d0f6;
						- _myState = 12288;
						- _body = "theRedLed.GEN(evFlash(100));";
					}
				}
				{ IState 
					- _id = OLDID 1424988 482;
					- _myState = 8192;
					- _name = "intrusion";
					- _parent = OLDID 1424988 461;
					- _defaultTrans = ;
					- _entryAction = { IAction 
						- _id = GUID 9f27f3fa-df03-11d2-ab10-0010a4f1d0f6;
						- _myState = 12288;
						- _body = "theSiren.setSirenOn();
theRedLed.GEN(evOn);";
					}
					- _exitAction = { IAction 
						- _id = GUID 9f27f3fb-df03-11d2-ab10-0010a4f1d0f6;
						- _body = "theSiren.setSirenOff();
";
					}
				}
				{ IState 
					- _id = OLDID 1424988 488;
					- _myState = 8192;
					- _name = "silence";
					- _parent = OLDID 1424988 461;
					- _defaultTrans = ;
				}
				{ IState 
					- _id = OLDID 1424988 461;
					- _myState = 8192;
					- _name = "active";
					- _parent = OLDID 1424988 452;
					- _defaultTrans = OLDID 1424988 462;
					- _entryAction = { IAction 
						- _id = GUID 9f27f3f5-df03-11d2-ab10-0010a4f1d0f6;
						- _myState = 12288;
						- _body = "//theLightController->GEN(evStartPresence);
theRedLed.GEN(evOn);";
					}
					- _exitAction = { IAction 
						- _id = GUID 9f27f3f6-df03-11d2-ab10-0010a4f1d0f6;
						- _body = "//theLightController->GEN(evStopPresence);
";
					}
				}
				{ IState 
					- _id = OLDID 1424988 494;
					- _myState = 8192;
					- _name = "exiting";
					- _parent = OLDID 1424988 452;
					- _defaultTrans = ;
					- _entryAction = { IAction 
						- _id = GUID 3c06c7e3-2887-11d3-ac08-0010a4f1d0f6;
						- _myState = 12288;
						- _body = "theRedLed.GEN(evFlash(100));";
					}
				}
				{ IState 
					- _id = OLDID 1424988 452;
					- _myState = 8192;
					- _name = "armed";
					- _parent = OLDID 1424988 448;
					- _defaultTrans = OLDID 1424988 458;
					- _exitAction = { IAction 
						- _id = GUID b0990f57-28a3-11d3-ac09-0010a4f1d0f6;
						- _body = "theRedLed.GEN(evOff);";
					}
				}
				{ IState 
					- _id = OLDID 1424988 500;
					- _myState = 8192;
					- _name = "off";
					- _parent = OLDID 1424988 448;
					- _defaultTrans = ;
				}
				{ IState 
					- _id = OLDID 1424988 448;
					- _myState = 8192;
					- _name = "ROOT";
					- _parent = ;
					- _defaultTrans = OLDID 1424988 449;
				}
			}
			- _version = 1.9.2005::15:56:6;
			- _baseVersion = 1.2.1990::0:0:0;
		}
	}
	- _itsStateChart = { IHandle 
		- _m2Class = "IStateChart";
		- _id = OLDID 1424988 447;
	}
	- Associations = { IRPYRawContainer 
		- size = 5;
		- value = 
		{ IPart 
			- _id = GUID 3c06b608-2887-11d3-ac08-0010a4f1d0f6;
			- _name = "theGreenLed";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "Led";
				- _id = GUID 3c06b5fc-2887-11d3-ac08-0010a4f1d0f6;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
		}
		{ IPart 
			- _id = GUID 3c06b604-2887-11d3-ac08-0010a4f1d0f6;
			- _name = "theRedLed";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "Led";
				- _id = GUID 3c06b5fc-2887-11d3-ac08-0010a4f1d0f6;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
		}
		{ IPart 
			- _id = OLDID 1424988 445;
			- _name = "theLightController";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "LightController";
				- _id = OLDID 1424988 241;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
		}
		{ IPart 
			- _id = OLDID 1424988 446;
			- _name = "theKeypad";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "Keypad";
				- _id = OLDID 1424988 98;
			}
			- _inverse = { IHandle 
				- _m2Class = "IAssociationEnd";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "Keypad";
				- _name = "itsController";
				- _id = GUID 791d70e3-3b6f-11d3-ac5d-0010a4f1d0f6;
			}
		}
		{ IPart 
			- _id = GUID 367f5a19-1fbb-436d-b3a3-a249a50142d0;
			- _name = "theSiren";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "";
				- _name = "SirenController";
				- _id = GUID 83c0b85e-6d7b-47cf-8686-8c6297f97500;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
		}
	}
	- ObjectLinks = { IRPYRawContainer 
		- size = 4;
		- value = 
		{ IObjectLink 
			- _id = GUID 33478f38-9648-450c-8987-f6b014811526;
			- _myState = 2048;
			- _name = "hwCtrl_theGreenLed";
			- _toLink = { IHandle 
				- _m2Class = "IPart";
				- _id = GUID 3c06b608-2887-11d3-ac08-0010a4f1d0f6;
			}
			- _fromLink = { IHandle 
				- _m2Class = "IPort";
				- _id = GUID ba9369fa-023b-4cfb-adc9-e922af45a638;
			}
			- _instantiates = { IMetaLinkHandle 
				- _m2Class = "";
			}
			- _end1Multiplicity = "1";
			- _end2Multiplicity = "1";
			- _toPort = { IHandle 
				- _m2Class = "IPort";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "Led";
				- _name = "hw";
				- _id = GUID cbf8abc2-63d9-4384-bcb2-0eb3ee2dc149;
			}
			- _fromPort = { IHandle 
				- _m2Class = "";
			}
		}
		{ IObjectLink 
			- _id = GUID 787d5f3a-7058-470a-941c-904a4323d391;
			- _myState = 2048;
			- _name = "hwCtrl_theRedLed";
			- _toLink = { IHandle 
				- _m2Class = "IPart";
				- _id = GUID 3c06b604-2887-11d3-ac08-0010a4f1d0f6;
			}
			- _fromLink = { IHandle 
				- _m2Class = "IPort";
				- _id = GUID ba9369fa-023b-4cfb-adc9-e922af45a638;
			}
			- _instantiates = { IMetaLinkHandle 
				- _m2Class = "";
			}
			- _end1Multiplicity = "1";
			- _end2Multiplicity = "1";
			- _toPort = { IHandle 
				- _m2Class = "IPort";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "Led";
				- _name = "hw";
				- _id = GUID cbf8abc2-63d9-4384-bcb2-0eb3ee2dc149;
			}
			- _fromPort = { IHandle 
				- _m2Class = "";
			}
		}
		{ IObjectLink 
			- _id = GUID 6a44253c-b8b3-41ac-8d6c-721926a74e06;
			- _myState = 2048;
			- _name = "theLightController_hwCtrl";
			- _toLink = { IHandle 
				- _m2Class = "IPort";
				- _id = GUID ba9369fa-023b-4cfb-adc9-e922af45a638;
			}
			- _fromLink = { IHandle 
				- _m2Class = "IPart";
				- _id = OLDID 1424988 445;
			}
			- _instantiates = { IMetaLinkHandle 
				- _m2Class = "";
			}
			- _end1Multiplicity = "1";
			- _end2Multiplicity = "1";
			- _toPort = { IHandle 
				- _m2Class = "";
			}
			- _fromPort = { IHandle 
				- _m2Class = "IPort";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "LightController";
				- _name = "hw";
				- _id = GUID 6893e67d-5c5d-451f-992b-013bb079a879;
			}
		}
		{ IObjectLink 
			- _id = GUID 183dc74b-6301-4c38-a157-6fdcefbee954;
			- _myState = 2048;
			- _name = "theSiren_hwCtrl";
			- _toLink = { IHandle 
				- _m2Class = "IPort";
				- _id = GUID ba9369fa-023b-4cfb-adc9-e922af45a638;
			}
			- _fromLink = { IHandle 
				- _m2Class = "IPart";
				- _id = GUID 367f5a19-1fbb-436d-b3a3-a249a50142d0;
			}
			- _instantiates = { IMetaLinkHandle 
				- _m2Class = "";
			}
			- _end1Multiplicity = "1";
			- _end2Multiplicity = "1";
			- _toPort = { IHandle 
				- _m2Class = "";
			}
			- _fromPort = { IHandle 
				- _m2Class = "IPort";
				- _filename = "AlarmPkg.sbs";
				- _subsystem = "AlarmPkg";
				- _class = "SirenController";
				- _name = "hw";
				- _id = GUID 2bb81142-d553-4959-9c9a-ced80a9cb1af;
			}
		}
	}
	- _classModifier = Unspecified;
}

