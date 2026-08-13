I-Logix-RPY-Archive version 8.6.0 C++ 4012249
{ IProject 
	- _id = GUID 32db9eaf-1458-4c1b-96a3-ff8cbfff595e;
	- _myState = 8192;
	- _properties = { IPropertyContainer 
		- Subjects = { IRPYRawContainer 
			- size = 2;
			- value = 
			{ IPropertySubject 
				- _Name = "CG";
				- Metaclasses = { IRPYRawContainer 
					- size = 3;
					- value = 
					{ IPropertyMetaclass 
						- _Name = "Class";
						- Properties = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IProperty 
								- _Name = "ReactiveSimpleComposites";
								- _Value = "True";
								- _Type = Bool;
							}
						}
					}
					{ IPropertyMetaclass 
						- _Name = "Component";
						- Properties = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IProperty 
								- _Name = "RelatedComponentsIncludePathInMakefile";
								- _Value = "False";
								- _Type = Bool;
							}
						}
					}
					{ IPropertyMetaclass 
						- _Name = "Configuration";
						- Properties = { IRPYRawContainer 
							- size = 4;
							- value = 
							{ IProperty 
								- _Name = "AddExplicitInitialInstancesToScope";
								- _Value = "True";
								- _Type = Bool;
							}
							{ IProperty 
								- _Name = "RemoveWhiteSpacesInBuildFile";
								- _Value = "True";
								- _Type = Bool;
							}
							{ IProperty 
								- _Name = "StrictExternalElementsGeneration";
								- _Value = "True";
								- _Type = Bool;
							}
							{ IProperty 
								- _Name = "SupportExternalElementsInScope";
								- _Value = "False";
								- _Type = Bool;
							}
						}
					}
				}
			}
			{ IPropertySubject 
				- _Name = "General";
				- Metaclasses = { IRPYRawContainer 
					- size = 1;
					- value = 
					{ IPropertyMetaclass 
						- _Name = "Model";
						- Properties = { IRPYRawContainer 
							- size = 1;
							- value = 
							{ IProperty 
								- _Name = "DiagramIsSavedUnit";
								- _Value = "True";
								- _Type = Bool;
							}
						}
					}
				}
			}
		}
	}
	- _name = "HomeAlarmWithPorts";
	- _lastID = 7;
	- Declaratives = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IStereotype 
			- _id = GUID f05c3d75-ada7-4e46-99f6-162ee33bce50;
			- _name = "Callback";
			- _m2Classes = { IRPYRawContainer 
				- size = 1;
				- value = "Dependency"; 
			}
		}
	}
	- _UserColors = { IRPYRawContainer 
		- size = 16;
		- value = 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 16777215; 
	}
	- _defaultSubsystem = { ISubsystemHandle 
		- _m2Class = "ISubsystem";
		- _filename = "AlarmPkg.sbs";
		- _subsystem = "";
		- _class = "";
		- _name = "AlarmPkg";
		- _id = OLDID 1424988 1;
	}
	- _component = { IHandle 
		- _m2Class = "IComponent";
		- _filename = "Gui.cmp";
		- _subsystem = "";
		- _class = "";
		- _name = "Gui";
		- _id = GUID af62d64f-df03-11d2-ab10-0010a4f1d0f6;
	}
	- Multiplicities = { IRPYRawContainer 
		- size = 5;
		- value = 
		{ IMultiplicityItem 
			- _name = "1";
			- _count = 20;
		}
		{ IMultiplicityItem 
			- _name = "*";
			- _count = -1;
		}
		{ IMultiplicityItem 
			- _name = "0,1";
			- _count = -1;
		}
		{ IMultiplicityItem 
			- _name = "1..*";
			- _count = -1;
		}
		{ IMultiplicityItem 
			- _name = "3";
			- _count = 0;
		}
	}
	- Subsystems = { IRPYRawContainer 
		- size = 8;
		- value = 
		{ ISubsystem 
			- fileName = "AlarmPkg";
			- _id = OLDID 1424988 1;
		}
		{ ISubsystem 
			- fileName = "HardwarePkg";
			- _id = GUID ac0adc67-3550-11d3-ac41-0010a4f1d0f6;
		}
		{ ISubsystem 
			- fileName = "TestPkg";
			- _id = GUID 9297b2a8-28b1-11d3-ac09-0010a4f1d0f6;
		}
		{ IProfile 
			- fileName = "CGCompatibilityPre75Cpp";
			- _id = GUID ca7332da-c15b-4929-9231-18e49a7edb64;
		}
		{ IProfile 
			- fileName = "CGCompatibilityPre751Cpp";
			- _id = GUID cb7b1c1a-b1ef-481f-9bc8-cb6d89a3848f;
		}
		{ IProfile 
			- fileName = "CGCompatibilityPre753Cpp";
			- _id = GUID 6d9087a8-4853-4759-a797-277df26481c2;
		}
		{ IProfile 
			- fileName = "CGCompatibilityPre76Cpp";
			- _id = GUID 38e0ad24-bc5e-45a0-9df2-1d0a857d19ad;
		}
		{ IProfile 
			- fileName = "CGCompatibilityPre761Cpp";
			- _id = GUID 0838347a-c933-40e3-9d7e-812acdb081ee;
		}
	}
	- Diagrams = { IRPYRawContainer 
		- size = 2;
		- value = 
		{ IDiagram 
			- fileName = "Packages";
			- _id = GUID 1864284c-49a3-4b98-b28f-0b41cb98e99e;
		}
		{ IDiagram 
			- fileName = "SampleOverview";
			- _id = GUID dc254baa-b631-4308-a530-64665859e0bc;
		}
	}
	- MSCS = { IRPYRawContainer 
		- size = 6;
		- value = 
		{ IMSC 
			- fileName = "Arming_the_alarm";
			- _id = GUID b5dccace-2874-11d3-ac08-0010a4f1d0f6;
			- _name = "Arming the alarm";
		}
		{ IMSC 
			- fileName = "Changing_the_code";
			- _id = GUID b5dcccb3-2874-11d3-ac08-0010a4f1d0f6;
			- _name = "Changing the code";
		}
		{ IMSC 
			- fileName = "Detecting_a_door_opening";
			- _id = GUID b5dcd31a-2874-11d3-ac08-0010a4f1d0f6;
			- _name = "Detecting a door opening";
		}
		{ IMSC 
			- fileName = "Detecting_a_movement";
			- _id = GUID b5dccfc2-2874-11d3-ac08-0010a4f1d0f6;
			- _name = "Detecting a movement";
		}
		{ IMSC 
			- fileName = "Powering_on";
			- _id = GUID b5dcd134-2874-11d3-ac08-0010a4f1d0f6;
			- _name = "Powering on";
		}
		{ IMSC 
			- fileName = "MSC1";
			- _id = GUID 42319bf9-50ff-4b5d-9dd6-dfee30d59375;
		}
	}
	- Components = { IRPYRawContainer 
		- size = 4;
		- value = 
		{ IComponent 
			- fileName = "Gui";
			- _id = GUID af62d64f-df03-11d2-ab10-0010a4f1d0f6;
		}
		{ IComponent 
			- fileName = "Test";
			- _id = GUID 9f27f676-df03-11d2-ab10-0010a4f1d0f6;
		}
		{ IComponent 
			- fileName = "HWAbsLib";
			- _id = GUID 5c208d45-6088-4348-9fc0-3c07d3135720;
		}
		{ IComponent 
			- fileName = "AlarmCtrlLib";
			- _id = GUID c5ba345b-6d0f-4168-8f1a-f5c31c5ae5d1;
		}
	}
	- ComponentDiagrams = { IRPYRawContainer 
		- size = 1;
		- value = 
		{ IComponentDiagram 
			- fileName = "AlarmCtrl_Vs._HW";
			- _id = GUID da83f0b8-a517-4a24-a425-73159c2a9e28;
			- _name = "AlarmCtrl Vs. HW";
		}
	}
}

