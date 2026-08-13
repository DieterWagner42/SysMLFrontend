// MFCguiDlg.cpp : implementation file
//

#include "stdafx.h"
#include "MFCgui.h"
#include "MFCguiDlg.h"
#include "mmsystem.h"

/////////////////////////////////////////////////////////////////////////////
// CAboutDlg dialog used for App About

class CAboutDlg : public CDialog
{
public:
	CAboutDlg();

// Dialog Data
	//{{AFX_DATA(CAboutDlg)
	enum { IDD = IDD_ABOUTBOX };
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CAboutDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	//{{AFX_MSG(CAboutDlg)
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

CAboutDlg::CAboutDlg() : CDialog(CAboutDlg::IDD)
{
	//{{AFX_DATA_INIT(CAboutDlg)
	//}}AFX_DATA_INIT
}

void CAboutDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CAboutDlg)
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CAboutDlg, CDialog)
	//{{AFX_MSG_MAP(CAboutDlg)
		// No message handlers
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CMFCguiDlg dialog

CMFCguiDlg::CMFCguiDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CMFCguiDlg::IDD, pParent)
{
	//{{AFX_DATA_INIT(CMFCguiDlg)
	m_Code = 0;
	//}}AFX_DATA_INIT
	// Note that LoadIcon does not require a subsequent DestroyIcon in Win32
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void CMFCguiDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CMFCguiDlg)
	DDX_Text(pDX, IDC_EDIT_CODE, m_Code);
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CMFCguiDlg, CDialog)
	//{{AFX_MSG_MAP(CMFCguiDlg)
	ON_WM_SYSCOMMAND()
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	ON_BN_CLICKED(IDC_BUTTON1, OnButton1)
	ON_BN_CLICKED(IDC_BUTTON2, OnButton2)
	ON_BN_CLICKED(IDC_BUTTON3, OnButton3)
	ON_BN_CLICKED(IDC_BUTTON4, OnButton4)
	ON_BN_CLICKED(IDC_BUTTON_ON, OnButtonOn)
	ON_BN_CLICKED(IDC_BUTTON_OFF, OnButtonOff)
	ON_BN_CLICKED(IDC_BUTTON_DOOR, OnButtonDoor)
	ON_WM_DESTROY()
	ON_WM_TIMER()
	ON_BN_CLICKED(IDC_BUTTON_ARM, OnButtonArm)
	ON_BN_CLICKED(IDC_BUTTON_DISARM, OnButtonDisarm)
	ON_BN_CLICKED(IDC_BUTTON5, OnButton5)
	ON_BN_CLICKED(IDC_BUTTON6, OnButton6)
	ON_BN_CLICKED(IDC_BUTTON7, OnButton7)
	ON_BN_CLICKED(IDC_BUTTON8, OnButton8)
	ON_BN_CLICKED(IDC_BUTTON9, OnButton9)
	ON_BN_CLICKED(IDC_BUTTON0, OnButton0)
	ON_BN_CLICKED(IDC_BUTTON_MOVEMENT, OnButtonMovement)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CMFCguiDlg message handlers

BOOL CMFCguiDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// Add "About..." menu item to system menu.

	// IDM_ABOUTBOX must be in the system command range.
	ASSERT((IDM_ABOUTBOX & 0xFFF0) == IDM_ABOUTBOX);
	ASSERT(IDM_ABOUTBOX < 0xF000);

	CMenu* pSysMenu = GetSystemMenu(FALSE);
	if (pSysMenu != NULL)
	{
		CString strAboutMenu;
		strAboutMenu.LoadString(IDS_ABOUTBOX);
		if (!strAboutMenu.IsEmpty())
		{
			pSysMenu->AppendMenu(MF_SEPARATOR);
			pSysMenu->AppendMenu(MF_STRING, IDM_ABOUTBOX, strAboutMenu);
		}
	}

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon
	
	// This is code added to replace main in Rhapsody
	OXF::initialize();

	//theAlarmController->configure( this );
	
	
    {
    	
    	getCtrl()->setItsIKeyListener(theAlarmCtrl.getHwObserver()->getItsIKeyListener());
    	
    	getCtrl()->setItsIMovementListener(theAlarmCtrl.getHwObserver()->getItsIMovementListener());
    	
    	getCtrl()->setItsIDoorListener(theAlarmCtrl.getHwObserver()->getItsIDoorListener());
    	
    }
    {
    	
    	theAlarmCtrl.getHwCtrl()->setItsILedCtrl(getCtrl()->getItsILedCtrl());
    	
    	theAlarmCtrl.getHwCtrl()->setItsILightCtrl(getCtrl()->getItsILightCtrl());
    	
    	theAlarmCtrl.getHwCtrl()->setItsISirenCtrl(getCtrl()->getItsISirenCtrl());
    	
    }
	
	
	
	theAlarmCtrl.startBehavior();

	Keypad* theKeypad = theAlarmCtrl.getTheKeypad();

	bitmapGreenLedOn.LoadBitmap  ( IDB_GREEN_LED_ON );
	bitmapGreenLedOff.LoadBitmap ( IDB_GREEN_LED_OFF );
	bitmapRedLedOn.LoadBitmap    ( IDB_RED_LED_ON );
	bitmapRedLedOff.LoadBitmap   ( IDB_RED_LED_OFF );

	m_Code = theKeypad->getOldCode();

    bRefresh = TRUE;
	UpdateData( FALSE );
	SetTimer ( 1, 300, NULL );
	SetWindowPos(&wndTopMost,300,0,0,0,SWP_NOSIZE|SWP_SHOWWINDOW);

	OXF::start(TRUE);

	return TRUE;  // return TRUE  unless you set the focus to a control
}

void CMFCguiDlg::OnSysCommand(UINT nID, LPARAM lParam)
{
	if ((nID & 0xFFF0) == IDM_ABOUTBOX)
	{
		CAboutDlg dlgAbout;
		dlgAbout.DoModal();
	}
	else
	{
		CDialog::OnSysCommand(nID, lParam);
	}
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CMFCguiDlg::OnPaint() 
{
	if (IsIconic())
	{
		CPaintDC dc(this); // device context for painting

		SendMessage(WM_ICONERASEBKGND, (WPARAM) dc.GetSafeHdc(), 0);

		// Center icon in client rectangle
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// Draw the icon
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CDialog::OnPaint();
	}
}

// The system calls this to obtain the cursor to display while the user drags
//  the minimized window.
HCURSOR CMFCguiDlg::OnQueryDragIcon()
{
	return (HCURSOR) m_hIcon;
}


void CMFCguiDlg::OnDestroy() 
{
	CDialog::OnDestroy();
	
	// TODO: Add your message handler code here
	KillTimer ( 1 );
}

void CMFCguiDlg::OnTimer(UINT nIDEvent) 
{
	if ( bRefresh = TRUE ) {
		Keypad *theKeypad = theAlarmCtrl.getTheKeypad();
 	m_Code = theKeypad->getOldCode();
	char sCode[15];
	itoa ( m_Code, sCode, 10 );

	UpdateData( FALSE );
    bRefresh = FALSE;
	}

	CDialog::OnTimer(nIDEvent);
}

void CMFCguiDlg::setSiren(const tOnOff& state ) 
{
	if ( state == ON )
		sndPlaySound ( "../../Sounds/cfalert.wav", SND_ASYNC | SND_LOOP );
	else
		sndPlaySound ( NULL, NULL );
}


void CMFCguiDlg::setLight(const tLights& aLight, const tOnOff& state ) 
{
	// nothing to do on this gui
}

void CMFCguiDlg::setLed(const tLed& ident, const tOnOff& state ) 
{
	CStatic* b;
	if ( ident == RED ) {
		b = (CStatic*)GetDlgItem( IDC_RED_LED );
		if ( state == ON )
		   b->SetBitmap ( HBITMAP(bitmapRedLedOn) );
		else
		   b->SetBitmap ( HBITMAP(bitmapRedLedOff) );
	}
	else {
		b = (CStatic*)GetDlgItem( IDC_GREEN_LED );
		if ( state == ON )
		   b->SetBitmap ( HBITMAP(bitmapGreenLedOn) );
		else
		   b->SetBitmap ( HBITMAP(bitmapGreenLedOff) );
	}
}


void CMFCguiDlg::OnButtonOn() 
{
	sndPlaySound ( "../../Sounds/tonelow.wav", SND_ASYNC );
	onKeyOn();	
}

void CMFCguiDlg::OnButtonOff() 
{
	sndPlaySound ( "../../Sounds/tonehigh.wav", SND_ASYNC );
	onKeyOff();
}

void CMFCguiDlg::OnButtonDoor() 
{
	onDoor();	
}

void CMFCguiDlg::OnButton0() 
{
	sndPlaySound ( "../../Sounds/tone0.wav", SND_ASYNC );
	onKey(0);	
}

void CMFCguiDlg::OnButton1() 
{
	sndPlaySound ( "../../Sounds/tone1.wav", SND_ASYNC );
	onKey(1);
}

void CMFCguiDlg::OnButton2() 
{
	sndPlaySound ( "../../Sounds/tone2.wav", SND_ASYNC );
	onKey(2);	
}

void CMFCguiDlg::OnButton3() 
{
	sndPlaySound ( "../../Sounds/tone3.wav", SND_ASYNC );
	onKey(3);
}

void CMFCguiDlg::OnButton4() 
{
	sndPlaySound ( "../../Sounds/tone4.wav", SND_ASYNC );
	onKey(4);
}

void CMFCguiDlg::OnButton5() 
{
	sndPlaySound ( "../../Sounds/tone5.wav", SND_ASYNC );
	onKey(5);	
}

void CMFCguiDlg::OnButton6() 
{
	sndPlaySound ( "../../Sounds/tone6.wav", SND_ASYNC );
	onKey(6);
}

void CMFCguiDlg::OnButton7() 
{
	sndPlaySound ( "../../Sounds/tone7.wav", SND_ASYNC );
	onKey(7);	
}

void CMFCguiDlg::OnButton8() 
{
	sndPlaySound ( "../../Sounds/tone8.wav", SND_ASYNC );
	onKey(8);	
}

void CMFCguiDlg::OnButton9() 
{
	sndPlaySound ( "../../Sounds/tone9.wav", SND_ASYNC );
	onKey(9);	
}

void CMFCguiDlg::OnButtonArm() 
{
	onArm();	
}

void CMFCguiDlg::OnButtonDisarm() 
{
	onDisarm();
}

void CMFCguiDlg::OnButtonMovement() 
{
	onMovement();
}
