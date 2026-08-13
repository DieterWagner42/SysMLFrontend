# Microsoft Developer Studio Generated NMAKE File, Based on MFCgui.dsp
!IF "$(CFG)" == ""
CFG=MFCgui - Win32 Debug
!MESSAGE No configuration specified. Defaulting to MFCgui - Win32 Debug.
!ENDIF 

!IF "$(CFG)" != "MFCgui - Win32 Release" && "$(CFG)" != "MFCgui - Win32 Debug"
!MESSAGE Invalid configuration "$(CFG)" specified.
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "MFCgui.mak" CFG="MFCgui - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "MFCgui - Win32 Release" (based on "Win32 (x86) Application")
!MESSAGE "MFCgui - Win32 Debug" (based on "Win32 (x86) Application")
!MESSAGE 
!ERROR An invalid configuration is specified.
!ENDIF 

!IF "$(OS)" == "Windows_NT"
NULL=
!ELSE 
NULL=nul
!ENDIF 

!IF  "$(CFG)" == "MFCgui - Win32 Release"

OUTDIR=.\Release
INTDIR=.\Release

ALL : "..\gui\release\Gui.exe"


CLEAN :
	-@erase "$(INTDIR)\MFCgui.obj"
	-@erase "$(INTDIR)\MFCgui.pch"
	-@erase "$(INTDIR)\MFCgui.res"
	-@erase "$(INTDIR)\MFCguiDlg.obj"
	-@erase "$(INTDIR)\StdAfx.obj"
	-@erase "$(INTDIR)\vc60.idb"
	-@erase "..\gui\release\Gui.exe"

"$(OUTDIR)" :
    if not exist "$(OUTDIR)/$(NULL)" mkdir "$(OUTDIR)"

CPP=cl.exe
CPP_PROJ=/nologo /MD /W3 /GX /O2 /I "..\Guilib\Release" /I "$(OMROOT)\langCpp" /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /D "_AFXDLL" /Fp"$(INTDIR)\MFCgui.pch" /Yu"stdafx.h" /Fo"$(INTDIR)\\" /Fd"$(INTDIR)\\" /FD /c 

.c{$(INTDIR)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(INTDIR)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(INTDIR)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.c{$(INTDIR)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(INTDIR)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(INTDIR)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

MTL=midl.exe
MTL_PROJ=/nologo /D "NDEBUG" /mktyplib203 /o "NUL" /win32 
RSC=rc.exe
RSC_PROJ=/l 0x409 /fo"$(INTDIR)\MFCgui.res" /d "NDEBUG" /d "_AFXDLL" 
BSC32=bscmake.exe
BSC32_FLAGS=/nologo /o"$(OUTDIR)\MFCgui.bsc" 
BSC32_SBRS= \
	
LINK32=link.exe
LINK32_FLAGS=..\guilib\release\guilib.lib $(OMROOT)\langCpp\lib\msoxfR.lib winmm.lib /nologo /subsystem:windows /incremental:no /pdb:"$(OUTDIR)\Gui.pdb" /machine:I386 /out:"..\gui\release\Gui.exe" 
LINK32_OBJS= \
	"$(INTDIR)\MFCgui.obj" \
	"$(INTDIR)\MFCguiDlg.obj" \
	"$(INTDIR)\StdAfx.obj" \
	"$(INTDIR)\MFCgui.res"

"..\gui\release\Gui.exe" : "$(OUTDIR)" $(DEF_FILE) $(LINK32_OBJS)
    $(LINK32) @<<
  $(LINK32_FLAGS) $(LINK32_OBJS)
<<

!ELSEIF  "$(CFG)" == "MFCgui - Win32 Debug"

OUTDIR=.\Debug
INTDIR=.\Debug

ALL : "..\Gui\Debug\gui.exe"


CLEAN :
	-@erase "$(INTDIR)\MFCgui.obj"
	-@erase "$(INTDIR)\MFCgui.pch"
	-@erase "$(INTDIR)\MFCgui.res"
	-@erase "$(INTDIR)\MFCguiDlg.obj"
	-@erase "$(INTDIR)\StdAfx.obj"
	-@erase "$(INTDIR)\vc60.idb"
	-@erase "$(INTDIR)\vc60.pdb"
	-@erase "$(OUTDIR)\gui.pdb"
	-@erase "..\Gui\Debug\gui.exe"
	-@erase "..\Gui\Debug\gui.ilk"

"$(OUTDIR)" :
    if not exist "$(OUTDIR)/$(NULL)" mkdir "$(OUTDIR)"

CPP=cl.exe
CPP_PROJ=/nologo /MDd /W3 /Gm /GX /ZI /Od /I "..\Guilib\Debug" /I "$(OMROOT)\langCpp" /D "WIN32" /D "_DEBUG" /D "_WINDOWS" /D "_AFXDLL" /D "OMANIMATOR" /Fp"$(INTDIR)\MFCgui.pch" /Yu"stdafx.h" /Fo"$(INTDIR)\\" /Fd"$(INTDIR)\\" /FD /c 

.c{$(INTDIR)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(INTDIR)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(INTDIR)}.obj::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.c{$(INTDIR)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cpp{$(INTDIR)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

.cxx{$(INTDIR)}.sbr::
   $(CPP) @<<
   $(CPP_PROJ) $< 
<<

MTL=midl.exe
MTL_PROJ=/nologo /D "_DEBUG" /mktyplib203 /o "NUL" /win32 
RSC=rc.exe
RSC_PROJ=/l 0x409 /fo"$(INTDIR)\MFCgui.res" /d "_DEBUG" /d "_AFXDLL" 
BSC32=bscmake.exe
BSC32_FLAGS=/nologo /o"$(OUTDIR)\MFCgui.bsc" 
BSC32_SBRS= \
	
LINK32=link.exe
LINK32_FLAGS=wsock32.lib ..\Guilib\Debug\guilib.lib $(OMROOT)\LangCpp\lib\msoxfinst.lib $(OMROOT)\LangCpp\lib\msomcomappl.lib $(OMROOT)\LangCpp\lib\msaomanim.lib winmm.lib /nologo /subsystem:windows /incremental:yes /pdb:"$(OUTDIR)\gui.pdb" /debug /machine:I386 /out:"..\Gui\Debug\gui.exe" /pdbtype:sept 
LINK32_OBJS= \
	"$(INTDIR)\MFCgui.obj" \
	"$(INTDIR)\MFCguiDlg.obj" \
	"$(INTDIR)\StdAfx.obj" \
	"$(INTDIR)\MFCgui.res"

"..\Gui\Debug\gui.exe" : "$(OUTDIR)" $(DEF_FILE) $(LINK32_OBJS)
    $(LINK32) @<<
  $(LINK32_FLAGS) $(LINK32_OBJS)
<<

!ENDIF 


!IF "$(NO_EXTERNAL_DEPS)" != "1"
!IF EXISTS("MFCgui.dep")
!INCLUDE "MFCgui.dep"
!ELSE 
!MESSAGE Warning: cannot find "MFCgui.dep"
!ENDIF 
!ENDIF 


!IF "$(CFG)" == "MFCgui - Win32 Release" || "$(CFG)" == "MFCgui - Win32 Debug"
SOURCE=.\MFCgui.cpp

"$(INTDIR)\MFCgui.obj" : $(SOURCE) "$(INTDIR)" "$(INTDIR)\MFCgui.pch"


SOURCE=.\MFCgui.rc

"$(INTDIR)\MFCgui.res" : $(SOURCE) "$(INTDIR)"
	$(RSC) $(RSC_PROJ) $(SOURCE)


SOURCE=.\MFCguiDlg.cpp

"$(INTDIR)\MFCguiDlg.obj" : $(SOURCE) "$(INTDIR)" "$(INTDIR)\MFCgui.pch"


SOURCE=.\StdAfx.cpp

!IF  "$(CFG)" == "MFCgui - Win32 Release"

CPP_SWITCHES=/nologo /MD /W3 /GX /O2 /I "..\Guilib\Release" /I "$(OMROOT)\langCpp" /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /D "_AFXDLL" /Fp"$(INTDIR)\MFCgui.pch" /Yc"stdafx.h" /Fo"$(INTDIR)\\" /Fd"$(INTDIR)\\" /FD /c 

"$(INTDIR)\StdAfx.obj"	"$(INTDIR)\MFCgui.pch" : $(SOURCE) "$(INTDIR)"
	$(CPP) @<<
  $(CPP_SWITCHES) $(SOURCE)
<<


!ELSEIF  "$(CFG)" == "MFCgui - Win32 Debug"

CPP_SWITCHES=/nologo /MDd /W3 /Gm /GX /ZI /Od /I "..\Guilib\Debug" /I "$(OMROOT)\langCpp" /D "WIN32" /D "_DEBUG" /D "_WINDOWS" /D "_AFXDLL" /D "OMANIMATOR" /Fp"$(INTDIR)\MFCgui.pch" /Yc"stdafx.h" /Fo"$(INTDIR)\\" /Fd"$(INTDIR)\\" /FD /c 

"$(INTDIR)\StdAfx.obj"	"$(INTDIR)\MFCgui.pch" : $(SOURCE) "$(INTDIR)"
	$(CPP) @<<
  $(CPP_SWITCHES) $(SOURCE)
<<


!ENDIF 


!ENDIF 

