I-Logix-RPY-Archive version 8.6.0 C++ 4012249
{ IClass 
	- _ownerHandle = { IHandle 
		- _m2Class = "ISubsystem";
		- _filename = "AbstractHW.sbs";
		- _subsystem = "HardwarePkg";
		- _class = "";
		- _name = "AbstractHW";
		- _id = GUID 16670831-dd39-4039-ae24-4b62f0088286;
	}
	- _id = OLDID 1424988 432;
	- _properties = { IPropertyContainer 
		- Subjects = { IRPYRawContainer 
			- size = 1;
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
		}
	}
	- _name = "AbsHardware";
	- _theMainDiagram = { IHandle 
		- _m2Class = "IDiagram";
		- _filename = "Possible_hardware_implementations.omd";
		- _subsystem = "HardwarePkg";
		- _class = "";
		- _name = "Possible hardware implementations";
		- _id = GUID ac0add42-3550-11d3-ac41-0010a4f1d0f6;
	}
	- _description = { IDescription 
		- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Hardware interface class, describing the operations that can be called on the hardware.\\f1\\rtlch\\par
}";
	}
	- _lastID = 6;
	- Declaratives = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IStructureDiagram 
			- _id = GUID a699cf60-7f58-4f05-aacb-069f09d7e747;
			- _myState = 8192;
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
								- _Name = "Class";
								- Properties = { IRPYRawContainer 
									- size = 6;
									- value = 
									{ IProperty 
										- _Name = "Fill.FillColor";
										- _Value = "255,255,255";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Font.Font";
										- _Value = "Tahoma";
										- _Type = String;
									}
									{ IProperty 
										- _Name = "Font.Size";
										- _Value = "8";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineColor";
										- _Value = "109,163,217";
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
								- _Name = "Interface";
								- Properties = { IRPYRawContainer 
									- size = 6;
									- value = 
									{ IProperty 
										- _Name = "Fill.FillColor";
										- _Value = "255,255,255";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Font.Font";
										- _Value = "Tahoma";
										- _Type = String;
									}
									{ IProperty 
										- _Name = "Font.Size";
										- _Value = "8";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineColor";
										- _Value = "109,163,217";
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
								- _Name = "Port";
								- Properties = { IRPYRawContainer 
									- size = 5;
									- value = 
									{ IProperty 
										- _Name = "Fill.FillColor";
										- _Value = "255,255,255";
										- _Type = Color;
									}
									{ IProperty 
										- _Name = "Font.Font";
										- _Value = "Tahoma";
										- _Type = String;
									}
									{ IProperty 
										- _Name = "Font.Size";
										- _Value = "8";
										- _Type = Int;
									}
									{ IProperty 
										- _Name = "Line.LineColor";
										- _Value = "109,163,217";
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
			- _name = "overview";
			- _lastModifiedTime = "1.12.2012::13:13:7";
			- _graphicChart = { CGIClassChart 
				- _id = GUID db1f5925-94c8-4db9-97ca-5f9e327d0c21;
				- m_type = 0;
				- m_pModelObject = { IHandle 
					- _m2Class = "IStructureDiagram";
					- _id = GUID a699cf60-7f58-4f05-aacb-069f09d7e747;
				}
				- m_pParent = ;
				- m_name = { CGIText 
					- m_str = "";
					- m_style = "Arial" 10 0 0 0 1 ;
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
				- m_drawBehavior = 0;
				- m_bIsPreferencesInitialized = 1;
				- elementList = 3;
				{ CGICompositeClass 
					- _id = GUID 1000ca77-fc47-4960-8ac2-de3d95bb89cd;
					- m_type = 78;
					- m_pModelObject = { IHandle 
						- _m2Class = "IClass";
						- _filename = "AbstractHW.sbs";
						- _subsystem = "HardwarePkg::AbstractHW";
						- _class = "";
						- _name = "TopLevel";
						- _id = GUID 7a9567cd-dbe0-4446-8787-da57f970244a;
					}
					- m_pParent = ;
					- m_name = { CGIText 
						- m_str = "TopLevel";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 0 0  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 5;
					}
					- m_drawBehavior = 0;
					- m_bIsPreferencesInitialized = 1;
					- m_AdditionalLabel = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 0 0  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 1;
					}
					- m_polygon = 4 50 50  393 50  393 313  50 313  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- Compartments = { IRPYRawContainer 
						- size = 0;
					}
				}
				{ CGIClass 
					- _id = GUID 59036f63-cfea-4872-99f7-e2f286fa8df9;
					- m_type = 105;
					- m_pModelObject = { IHandle 
						- _m2Class = "IClass";
						- _id = OLDID 1424988 432;
					}
					- m_pParent = GUID 1000ca77-fc47-4960-8ac2-de3d95bb89cd;
					- m_name = { CGIText 
						- m_str = "AbsHardware";
						- m_style = "Arial" 10 0 0 1 2 ;
						- m_color = { IColor 
							- m_fgColor = 44;
							- m_bgColor = 44;
							- m_bgFlag = 0;
						}
						- m_position = 1 10.6578947368421 11.5631578947368  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 5;
					}
					- m_drawBehavior = 26632;
					- m_transform = 0.283557 0 0 0.229694 49.1493 -10.869 ;
					- m_bIsPreferencesInitialized = 1;
					- m_AdditionalLabel = { CGIText 
						- m_str = "";
						- m_style = "Arial" 10 0 0 0 1 ;
						- m_color = { IColor 
							- m_fgColor = 0;
							- m_bgColor = 0;
							- m_bgFlag = 0;
						}
						- m_position = 1 0 0  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nOrientationCtrlPt = 1;
					}
					- m_polygon = 4 3 265  1195 265  1195 1410  3 1410  ;
					- m_nNameFormat = 0;
					- m_nIsNameFormat = 0;
					- Compartments = { IRPYRawContainer 
						- size = 0;
					}
					- Attrs = { IRPYRawContainer 
						- size = 0;
					}
					- Operations = { IRPYRawContainer 
						- size = 0;
					}
				}
				{ CGIPortConnector 
					- _id = GUID b214659a-13a7-4405-9ed7-eaba03090b97;
					- m_type = 57;
					- m_pModelObject = { IHandle 
						- _m2Class = "IPort";
						- _id = GUID 33b9ede6-a95f-4767-8557-d05c131c2814;
					}
					- m_pParent = GUID 59036f63-cfea-4872-99f7-e2f286fa8df9;
					- m_name = { CGIText 
						- m_str = "ctrl";
						- m_style = "Arial" 10 0 0 1 2 ;
						- m_color = { IColor 
							- m_fgColor = 44;
							- m_bgColor = 44;
							- m_bgFlag = 0;
						}
						- m_position = 4 401 170  433 170  433 189  401 189  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 16;
						- m_nVerticalSpacing = -19;
						- m_nOrientationCtrlPt = 8;
					}
					- m_drawBehavior = 4104;
					- m_transform = 0 4.35361 -3.52663 0 1332.6 697.228 ;
					- m_bIsPreferencesInitialized = 1;
					- m_position = 4 0 0  0 73  68 73  68 0  ;
					- m_pInheritsFrom = { IHandle 
						- _m2Class = "";
					}
					- m_nInheritanceMask = 0;
					- m_SubType = 0;
					- m_providedInterfaceLabel = { CGIText 
						- m_str = "ILedCtrl, ILightCtrl, ISirenCtrl";
						- m_style = "Arial" 8 0 0 1 2 ;
						- m_color = { IColor 
							- m_fgColor = 44;
							- m_bgColor = 44;
							- m_bgFlag = 0;
						}
						- m_position = 1 0 0  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 69;
						- m_nVerticalSpacing = 14;
						- m_nOrientationCtrlPt = 4;
					}
					- m_requiredInterfaceLabel = { CGIText 
						- m_str = "IKeyListener, IMovementListener, IDoorListener";
						- m_style = "Arial" 8 0 0 1 2 ;
						- m_color = { IColor 
							- m_fgColor = 44;
							- m_bgColor = 44;
							- m_bgFlag = 0;
						}
						- m_position = 1 0 0  ;
						- m_nIdent = 0;
						- m_bImplicitSetRectPoints = 0;
						- m_nHorizontalSpacing = 112;
						- m_nVerticalSpacing = -14;
						- m_nOrientationCtrlPt = 2;
					}
				}
				
				- m_access = 'Z';
				- m_modified = 'N';
				- m_fileVersion = "";
				- m_nModifyDate = 0;
				- m_nCreateDate = 0;
				- m_creator = "";
				- m_bScaleWithZoom = 1;
				- m_arrowStyle = 'S';
				- m_pRoot = GUID 1000ca77-fc47-4960-8ac2-de3d95bb89cd;
				- m_currentLeftTop = 0 0 ;
				- m_currentRightBottom = 0 0 ;
			}
			- _defaultSubsystem = { IHandle 
				- _m2Class = "ISubsystem";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg";
				- _class = "";
				- _name = "AbstractHW";
				- _id = GUID 16670831-dd39-4039-ae24-4b62f0088286;
			}
		}
	}
	- weakCGTime = 1.12.2012::13:13:7;
	- strongCGTime = 7.14.2003::9:17:16;
	- Ports = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IPort 
			- _id = GUID 33b9ede6-a95f-4767-8557-d05c131c2814;
			- _name = "ctrl";
			- _multiplicity = "1";
			- _otherClass = { IClassifierHandle 
				- _m2Class = "IClass";
				- _id = GUID 04b56d01-a545-4fa2-b928-4b24bc281197;
			}
			- _inverse = { IHandle 
				- _m2Class = "";
			}
			- _implicitClass = { IClass 
				- _id = GUID 04b56d01-a545-4fa2-b928-4b24bc281197;
				- _myState = 8192;
				- _name = "ctrl_implicitContract";
				- Dependencies = { IRPYRawContainer 
					- size = 3;
					- value = 
					{ IDependency 
						- _id = GUID 111b0a75-2f7d-475e-8830-df4226e40694;
						- _name = "IKeyObserver";
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
							- _name = "IKeyListener";
							- _id = GUID db9992d8-3be9-4867-8871-bb48bc2d5baa;
						}
					}
					{ IDependency 
						- _id = GUID a83e8cbc-1311-46fe-9457-a36489a12cbd;
						- _name = "IMovementListener";
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
							- _name = "IMovementListener";
							- _id = GUID 065ba526-937e-4fd4-b77d-4e2049faad9c;
						}
					}
					{ IDependency 
						- _id = GUID cfc73ca7-8c8a-4e46-ac90-fd009d75ef4d;
						- _name = "IDoorListener";
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
							- _name = "IDoorListener";
							- _id = GUID e65ab3c7-2dfd-44cd-93ca-b895d7f57007;
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
				- weakCGTime = 7.10.2003::10:52:24;
				- strongCGTime = 7.10.2003::10:52:24;
				- Inheritances = { IRPYRawContainer 
					- size = 3;
					- value = 
					{ IGeneralization 
						- _id = GUID 8efcae74-d3b7-4cbd-bff5-9a6bdb8ae45f;
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "ILedCtrl";
							- _id = GUID e55ab0dd-a2eb-4a76-bc70-c9a9afd95b97;
						}
						- _inheritanceType = iPublic;
						- _isVirtual = 0;
					}
					{ IGeneralization 
						- _id = GUID f37eb721-1888-41a9-8a76-c62772ba4780;
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "ILightCtrl";
							- _id = GUID 1df77aff-fcf1-495b-815a-23e9348577e9;
						}
						- _inheritanceType = iPublic;
						- _isVirtual = 0;
					}
					{ IGeneralization 
						- _id = GUID 8c8ffb26-6908-4d44-92d1-649e029c59cd;
						- _dependsOn = { INObjectHandle 
							- _m2Class = "IClass";
							- _filename = "AbstractHW.sbs";
							- _subsystem = "HardwarePkg::AbstractHW";
							- _class = "";
							- _name = "ISirenCtrl";
							- _id = GUID 9b3268e8-cd8a-42da-b528-175797358715;
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
		- size = 10;
		- value = 
		{ IPrimitiveOperation 
			- _id = OLDID 1424988 437;
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
										- _Name = "Kind";
										- _Value = "abstract";
										- _Type = Enum;
										- _ExtraTypeInfo = "common,abstract";
									}
								}
							}
						}
					}
				}
			}
			- _name = "setLight";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 switch a light on or off\\par
\\par
<< ABSTRACT >> Must be overridden \\f1\\rtlch\\par
}
";
			}
			- _virtual = 1;
			- Args = { IRPYRawContainer 
				- size = 2;
				- value = 
				{ IArgument 
					- _id = OLDID 1424988 438;
					- _name = "aLight";
					- _typeOf = { IHandle 
						- _m2Class = "IType";
						- _filename = "HardwarePkg.sbs";
						- _subsystem = "HardwarePkg";
						- _class = "";
						- _name = "tLights";
						- _id = OLDID 1424988 326;
					}
					- _isOrdered = 0;
					- _argumentDirection = In;
				}
				{ IArgument 
					- _id = GUID b5dcded5-2874-11d3-ac08-0010a4f1d0f6;
					- _name = "state";
					- _typeOf = { IHandle 
						- _m2Class = "IType";
						- _filename = "HardwarePkg.sbs";
						- _subsystem = "HardwarePkg";
						- _class = "";
						- _name = "tOnOff";
						- _id = GUID b5dcdd05-2874-11d3-ac08-0010a4f1d0f6;
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
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b0a-27f5-11d3-ac07-0010a4f1d0f6;
			- _name = "onArm";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Called to arm the alarm controller\\f1\\rtlch\\par
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
				- _bodyData = "OUT_PORT(ctrl)->onArm();
/*
assert(itsAlarmController);
itsAlarmController->onArm();
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b0d-27f5-11d3-ac07-0010a4f1d0f6;
			- _name = "onDisarm";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Called to disarm the alarm controller\\f1\\rtlch\\par
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
				- _bodyData = "OUT_PORT(ctrl)->onDisarm();
/*
assert(itsAlarmController);
itsAlarmController->onDisarm();
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b12-27f5-11d3-ac07-0010a4f1d0f6;
			- _name = "onKeyOff";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Called whenever the off key is pressed\\f1\\rtlch\\par
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
				- _bodyData = "OUT_PORT(ctrl)->onKeyOff();
/*
assert(itsAlarmController);
itsAlarmController->onKeyOff();
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b15-27f5-11d3-ac07-0010a4f1d0f6;
			- _name = "onKeyOn";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Called whenever the on key is pressed\\f1\\rtlch\\par
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
				- _bodyData = "OUT_PORT(ctrl)->onKeyOn();
/*
assert(itsAlarmController);
itsAlarmController->onKeyOn();
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b1b-27f5-11d3-ac07-0010a4f1d0f6;
			- _name = "onKey";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 Called whenever a numerical key is pressed\\f1\\rtlch\\par
}
";
			}
			- _virtual = 0;
			- Args = { IRPYRawContainer 
				- size = 1;
				- value = 
				{ IArgument 
					- _id = GUID 0c594b17-27f5-11d3-ac07-0010a4f1d0f6;
					- _name = "n";
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
				- _bodyData = "OUT_PORT(ctrl)->onKey(n);
/*
assert(itsAlarmController);
itsAlarmController->onKey(n);
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b1e-27f5-11d3-ac07-0010a4f1d0f6;
			- _name = "onMovement";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 routine called when a movement has been detected\\f1\\rtlch\\par
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
				- _bodyData = "OUT_PORT(ctrl)->onMovement();
/*
assert(itsAlarmController);
itsAlarmController->onMovement();
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID 0c594b21-27f5-11d3-ac07-0010a4f1d0f6;
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
				- _bodyData = "OUT_PORT(ctrl)->onDoor();
/*
assert(itsAlarmController);
itsAlarmController->onDoor();
*/";
			}
		}
		{ IPrimitiveOperation 
			- _id = GUID b5dcdc70-2874-11d3-ac08-0010a4f1d0f6;
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
										- _Name = "Kind";
										- _Value = "abstract";
										- _Type = Enum;
										- _ExtraTypeInfo = "common,abstract";
									}
								}
							}
						}
					}
				}
			}
			- _name = "setLed";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 switch an LED on or off\\par
\\par
<< ABSTRACT >> Must be overridden \\f1\\rtlch\\par
}
";
			}
			- _virtual = 1;
			- Args = { IRPYRawContainer 
				- size = 2;
				- value = 
				{ IArgument 
					- _id = GUID 64309fdd-288f-11d3-ac08-0010a4f1d0f6;
					- _name = "ident";
					- _typeOf = { IHandle 
						- _m2Class = "IType";
						- _filename = "HardwarePkg.sbs";
						- _subsystem = "HardwarePkg";
						- _class = "";
						- _name = "tLed";
						- _id = GUID 3c06b99b-2887-11d3-ac08-0010a4f1d0f6;
					}
					- _isOrdered = 0;
					- _argumentDirection = In;
				}
				{ IArgument 
					- _id = GUID b5dcdf6c-2874-11d3-ac08-0010a4f1d0f6;
					- _name = "state";
					- _typeOf = { IHandle 
						- _m2Class = "IType";
						- _filename = "HardwarePkg.sbs";
						- _subsystem = "HardwarePkg";
						- _class = "";
						- _name = "tOnOff";
						- _id = GUID b5dcdd05-2874-11d3-ac08-0010a4f1d0f6;
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
		}
		{ IPrimitiveOperation 
			- _id = GUID b2d25340-6f97-11d5-8739-00500409ec93;
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
										- _Name = "Kind";
										- _Value = "abstract";
										- _Type = Enum;
										- _ExtraTypeInfo = "common,abstract";
									}
								}
							}
						}
					}
				}
			}
			- _name = "setSiren";
			- _displayName = "Message_3";
			- _description = { IDescription 
				- _textRTF = "{\\rtf1\\fbidis\\ansi\\ansicpg1255\\deff0\\deflang1037{\\fonttbl{\\f0\\fnil\\fcharset0 MS Sans Serif;}{\\f1\\fnil\\fcharset177 MS Sans Serif;}}
\\viewkind4\\uc1\\pard\\ltrpar\\f0\\fs17 << ABSTRACT >> Must be overridden \\f1\\rtlch\\par
}
";
			}
			- _virtual = 1;
			- Args = { IRPYRawContainer 
				- size = 1;
				- value = 
				{ IArgument 
					- _id = GUID b2d2536f-6f97-11d5-8739-00500409ec93;
					- _name = "state";
					- _typeOf = { IHandle 
						- _m2Class = "IType";
						- _filename = "HardwarePkg.sbs";
						- _subsystem = "HardwarePkg";
						- _class = "";
						- _name = "tOnOff";
						- _id = GUID b5dcdd05-2874-11d3-ac08-0010a4f1d0f6;
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
		}
	}
	- Inheritances = { IRPYRawContainer 
		- size = 3;
		- value = 
		{ IGeneralization 
			- _id = GUID d2d55be0-2de6-46a4-9f4b-36280b6bba74;
			- _dependsOn = { INObjectHandle 
				- _m2Class = "IClass";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg::AbstractHW";
				- _class = "";
				- _name = "ILedCtrl";
				- _id = GUID e55ab0dd-a2eb-4a76-bc70-c9a9afd95b97;
			}
			- _inheritanceType = iPublic;
			- _isVirtual = 0;
		}
		{ IGeneralization 
			- _id = GUID 6bf68928-f9e7-47b0-b209-5ae4b41ffb1f;
			- _dependsOn = { INObjectHandle 
				- _m2Class = "IClass";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg::AbstractHW";
				- _class = "";
				- _name = "ILightCtrl";
				- _id = GUID 1df77aff-fcf1-495b-815a-23e9348577e9;
			}
			- _inheritanceType = iPublic;
			- _isVirtual = 0;
		}
		{ IGeneralization 
			- _id = GUID 4bd8ea94-8d9a-441c-be84-14cab68edb79;
			- _dependsOn = { INObjectHandle 
				- _m2Class = "IClass";
				- _filename = "AbstractHW.sbs";
				- _subsystem = "HardwarePkg::AbstractHW";
				- _class = "";
				- _name = "ISirenCtrl";
				- _id = GUID 9b3268e8-cd8a-42da-b528-175797358715;
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

