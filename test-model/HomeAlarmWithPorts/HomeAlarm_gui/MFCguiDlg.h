// MFCguiDlg.h : header file
//

#include "AlarmPkg.h"
#include "AlarmController.h"
#include "LightController.h"
#include "Keypad.h"
#include "AbsHardware.h"

#if !defined(AFX_MFCGUIDLG_H__01F068D7_4BE7_11D2_A881_9C3223000000__INCLUDED_)
#define AFX_MFCGUIDLG_H__01F068D7_4BE7_11D2_A881_9C3223000000__INCLUDED_

#if _MSC_VER >= 1000
#pragma once
#endif // _MSC_VER >= 1000

/////////////////////////////////////////////////////////////////////////////
// CMFCguiDlg dialog

class CMFCguiDlg : public CDialog, AbsHardware
{
// Construction
public:
	CMFCguiDlg(CWnd* pParent = NULL);	// standard constructor

	AlarmController theAlarmCtrl;

	//AlarmController* theAlarmController;
	//LightController* theLightController;
	//Keypad* theKeypad;
	bool bRefresh;

	CBitmap bitmapGreenLedOn;
	CBitmap bitmapGreenLedOff;
	CBitmap bitmapRedLedOn;
	CBitmap bitmapRedLedOff;


// Dialog Data
	//{{AFX_DATA(CMFCguiDlg)
	enum { IDD = IDD_MFCGUI_DIALOG };
	int		m_Code;
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CMFCguiDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support
	//}}AFX_VIRTUAL

	// Inherited overridden operations
	void setSiren ( const tOnOff& state );
	void setLight ( const tLights& aLight, const tOnOff& state );
	void setLed   ( const tLed& ident, const tOnOff& state );


// Implementation
protected:
	HICON m_hIcon;

	// Generated message map functions
	//{{AFX_MSG(CMFCguiDlg)
	virtual BOOL OnInitDialog();
	afx_msg void OnSysCommand(UINT nID, LPARAM lParam);
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	afx_msg void OnButton1();
	afx_msg void OnButton2();
	afx_msg void OnButton3();
	afx_msg void OnButton4();
	afx_msg void OnButtonOn();
	afx_msg void OnButtonOff();
	afx_msg void OnButtonDoor();
	afx_msg void OnDestroy();
	afx_msg void OnTimer(UINT nIDEvent);
	afx_msg void OnButtonArm();
	afx_msg void OnButtonDisarm();
	afx_msg void OnButton5();
	afx_msg void OnButton6();
	afx_msg void OnButton7();
	afx_msg void OnButton8();
	afx_msg void OnButton9();
	afx_msg void OnButton0();
	afx_msg void OnButtonMovement();
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

//{{AFX_INSERT_LOCATION}}
// Microsoft Developer Studio will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_MFCGUIDLG_H__01F068D7_4BE7_11D2_A881_9C3223000000__INCLUDED_)
