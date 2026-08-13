// MFCgui.h : main header file for the MFCGUI application
//

#if !defined(AFX_MFCGUI_H__01F068D5_4BE7_11D2_A881_9C3223000000__INCLUDED_)
#define AFX_MFCGUI_H__01F068D5_4BE7_11D2_A881_9C3223000000__INCLUDED_

#if _MSC_VER >= 1000
#pragma once
#endif // _MSC_VER >= 1000

#ifndef __AFXWIN_H__
	#error include 'stdafx.h' before including this file for PCH
#endif

#include "resource.h"		// main symbols

/////////////////////////////////////////////////////////////////////////////
// CMFCguiApp:
// See MFCgui.cpp for the implementation of this class
//

class CMFCguiApp : public CWinApp
{
public:
	CMFCguiApp();

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CMFCguiApp)
	public:
	virtual BOOL InitInstance();
	//}}AFX_VIRTUAL

// Implementation

	//{{AFX_MSG(CMFCguiApp)
		// NOTE - the ClassWizard will add and remove member functions here.
		//    DO NOT EDIT what you see in these blocks of generated code !
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};


/////////////////////////////////////////////////////////////////////////////

//{{AFX_INSERT_LOCATION}}
// Microsoft Developer Studio will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_MFCGUI_H__01F068D5_4BE7_11D2_A881_9C3223000000__INCLUDED_)
