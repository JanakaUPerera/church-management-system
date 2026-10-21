# Church Management System — User Manual

**Version:** 1.1.0  
**Last Updated:** September 2026  

**Developed By:** Janaka Perera  
**Contact Number:** 0779156683  
**Email:** janakaudayangaperera@gmail.com

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Installation & First-Time Setup](#2-installation--first-time-setup)
3. [Getting Started](#3-getting-started)
4. [Managing Regions](#4-managing-regions)
5. [Managing Churches](#5-managing-churches)
6. [Receipt Entry](#6-receipt-entry)
7. [Receipt History](#7-receipt-history)
8. [Submission Status](#8-submission-status)
9. [Reports](#9-reports)
10. [User Management](#10-user-management)
11. [Roles & Permissions](#11-roles--permissions)
12. [Backup & Restore](#12-backup--restore)
13. [Activity Logs](#13-activity-logs)
14. [SMS Logs](#14-sms-logs)
15. [Settings](#15-settings)
16. [My Profile](#16-my-profile)
17. [Troubleshooting](#17-troubleshooting)
18. [Glossary](#18-glossary)
19. [Finance](#19-finance)

---

## 1. Introduction

### 1.1 What is the Church Management System?

The Church Management System is a desktop application designed to help churches manage their weekly collection records, track submission status across regions, generate reports, and communicate with church leaders via SMS notifications.

### 1.2 Key Features

- **Receipt Management** — Create, view, cancel, and reprint weekly collection receipts
- **Multi-Region Support** — Organize churches into regions for regional tracking
- **SMS Notifications** — Automatically send receipt confirmations to churches via SMS
- **Comprehensive Reporting** — 15 report types with PDF and Excel export
- **Submission Tracking** — Real-time dashboard showing which churches have submitted
- **Backup & Restore** — Automated and manual database backup with restore capability
- **Role-Based Access Control** — Granular permissions for different user roles
- **Multi-Language Receipts** — Support for English, Sinhala, and Tamil receipts
- **Audit Trail** — Complete activity logging for all system actions

### 1.3 System Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| Operating System | Windows 10 | Windows 10/11 |
| RAM | 128 MB | 512 MB |
| Disk Space | 500 MB | 1 GB |
| Display | 1024 × 768 | 1920 × 1080 |
| Database Server | MySQL 8.0+ | MySQL 9.7 |
| .NET Framework | 4.6.1+ (for installer) | Latest |
| VC++ Redistributable | 2019+ | Latest |

---

## 2. Installation & First-Time Setup

### 2.1 Installing the Application

1. Run the installer file (`ChurchManagementSystem-1.0.0-setup.exe`).
2. Follow the on-screen installation wizard.
3. Choose the installation folder (default: `C:\Program Files\Church Management System`).
4. The installer may optionally install:
   - **VC++ Redistributable** — Required system component
   - **MySQL Server** — Database server (only if not already installed)
5. Click **Install** and wait for the installation to complete.
6. Launch the application from the desktop shortcut or Start Menu.

### 2.2 First-Time Database Setup

When you launch the application for the first time, the **Database Setup Wizard** will appear automatically.

#### Step 1: Enter Database Connection Details

| Field | Description | Example |
|-------|-------------|---------|
| **Server Host** | The computer where MySQL is installed | `localhost` or `192.168.1.102` |
| **Port** | MySQL port number (default: 3306) | `3306` |
| **Database Name** | Name for the church database | `church_management_system` |
| **Username** | MySQL user with admin privileges | `root` |
| **Password** | Password for the MySQL admin user | *(your MySQL password)* |

> **Note:** The database name and username can only contain letters, numbers, and underscores.

#### Step 2: Test and Initialize

1. Click **Initialize Database**.
2. The system will:
   - Connect to MySQL as an administrator
   - Create the database (if it does not exist)
   - Create a dedicated application user
   - Set up all required database tables
   - Load system configuration
3. Watch for green checkmarks (✓) indicating successful steps.
4. If any step shows a red cross (✗), check the error message and try again.

#### Step 3: Save Configuration

1. Once initialization succeeds, click **Save**.
2. The configuration will be saved for future launches.

> **Warning:** Do not close the application during database initialization. This may corrupt the database setup.

### 2.3 Multi-Machine Setup

If you need to use the system on multiple computers:

| Machine Type | Purpose | Settings |
|--------------|---------|----------|
| **Primary (Server)** | Runs database migrations, scheduled backups | Set `db.run-migrations=true` |
| **Secondary (Client)** | Connects to the server database, manual backups only | Set `db.run-migrations=false` |

The `db.run-migrations` setting is configured in the `application.properties` file at:
`%APPDATA%\Church Management System\application.properties`

---

## 3. Getting Started

### 3.1 Login

1. Open the application.
2. The **Startup Check** screen will verify database and modem connections.
3. Once checks pass, the **Login** screen appears.
4. Enter your **Username** and **Password**.
5. Click **Login**.

> **Note:** If this is your first login, your administrator will provide your username and temporary password.

### 3.2 Force Password Change

If your administrator has flagged your account for a mandatory password change:

1. Enter your **Current Password**.
2. Enter and confirm your **New Password**.
3. Click **Change Password**.
4. You will then be taken to the dashboard.

### 3.3 Understanding the Dashboard

The dashboard is your main workspace with these elements:

**Header (Top Bar):**
- Organization name
- Your name and role
- Database connection status (green = connected, red = disconnected)
- Modem connection status (if SMS is enabled)
- Window controls (minimize, maximize, close)

**Sidebar (Left Panel):**
- **Home** (top) returns to the module tiles
- The current module's name — click it to switch to another module
- Only the current module's screens, shown/hidden based on your permissions

**Footer (Bottom Bar):**
- Current time and date
- Application version number

### 3.4 Getting Around

The system is organised into **modules**: Dashboard, Collections, Branches, Messaging and Administration.

- **Home** shows one tile per module you can use. Each tile has a short description and may show a badge — for example *12 pending* on Collections (branches that have not submitted this week) or *3 failed* on Messaging (SMS that failed in the last 7 days). Click a tile, or select it with **Tab** and press **Enter**, to open the module.
- The **(i)** button on a tile lists the module's screens with a one-line explanation of each, without opening it. Press **Esc** to close the list, or **Open module** to go in.
- Inside a module the sidebar shows only that module's screens. Click the module name at the top of the sidebar to jump straight to another module.
- The title line shows where you are, e.g. **Home › Collections › Receipt History**. Click any part to go back to it.
- Re-opening a module returns to the screen you last used in it.
- **Keyboard shortcuts:** **Ctrl+H** Home · **Ctrl+1** Dashboard · **Ctrl+2** Collections · **Ctrl+3** Branches · **Ctrl+4** Messaging · **Ctrl+7** Administration.
- **Start screen:** after login you go straight to your own start screen. Choose it in **My Profile → Open after login** (an administrator can also set it in **Users**). If you lose access to that screen, the system opens Dashboard and tells you in the status bar.
- If you leave **Receipt** or **Send Message** with unsaved input, the system asks before discarding it.
- The sidebar can be collapsed with the **«** button; hover over an icon to see its name.

### 3.5 Window Controls

- **Drag** the header bar to move the window
- **Double-click** the header to maximize or restore
- Use the minimize (—), maximize (□), and close (×) buttons in the top-right corner

---

## 4. Managing Regions

Regions are geographic areas that group your churches (e.g., "Western Province" or "Colombo District").

### 4.1 Viewing Regions

1. Open **Branches › Regions**.
2. The regions table displays all regions with:
   - **Code** — Short identifier (e.g., `REG001`)
   - **Name** — Full region name
   - **Status** — Active (green badge) or Inactive (red badge)
   - **Created At** — Date the region was added
   - **Actions** — Edit and toggle status buttons

### 4.2 Creating a New Region

1. Click the **+ Create Region** button.
2. Fill in the form:
   - **Region Code** — A unique short code (e.g., `WEST01`)
   - **Region Name** — Full descriptive name (e.g., "Western Province")
   - **Status** — Select Active or Inactive
3. Click **Save**.
4. A success message confirms: *"Region created successfully."*

### 4.3 Editing a Region

1. Click the **Edit** (pencil) icon on the region row.
2. Modify the fields as needed.
3. Click **Save**.

### 4.4 Deactivating a Region

1. Click the **Deactivate** button (or toggle switch) on the region row.
2. Confirm the action in the dialog.
3. The region status changes to **Inactive**.

> **Note:** Deactivating a region hides it from active workflows (receipt entry, church selection) but does **not** delete it. Existing data is preserved.

### 4.5 Searching and Filtering

- Use the **search box** to filter regions by code, name, or status.
- Click **Refresh** to clear the search and reload all regions.
- Use the pagination controls at the bottom to navigate through results.

### 4.6 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View regions | `region.menu.view` |
| Create region | `region.create` |
| Edit region | `region.update` |
| Activate/Deactivate | `region.delete` |

---

## 5. Managing Churches

Churches are the individual congregations within your regions. Each church has an authorized contact person and optional SMS notification settings.

### 5.1 Viewing Churches

1. Open **Branches › Churches**.
2. The table displays:
   - **Code** — Church identifier (e.g., `CH001`)
   - **Name** — Church name
   - **Region** — Associated region
   - **Authorized Person** — Contact person's name
   - **Position** — Their role (Pastor, Treasurer, etc.)
   - **SMS Number** — Mobile number for notifications
   - **Receipt Language** — Preferred language (English/Sinhala/Tamil)
   - **Status** — Active or Inactive

### 5.2 Creating a New Church

1. Click the **+ Create Church** button.
2. Fill in the form:

| Field | Description | Required |
|-------|-------------|----------|
| **Church Code** | Unique identifier | Yes |
| **Church Name** | Full church name | Yes |
| **Region** | Select from dropdown | Yes |
| **Status** | Active or Inactive | Yes |
| **Authorized Person Name** | Contact person | Yes |
| **Position** | Pastor, Treasurer, Secretary, Church Leader, Brother, Sister, or Other | Yes |
| **Other Position** | Specify if "Other" is selected | Conditional |
| **SMS Mobile Number** | For receipt notifications | No |
| **Receipt Language** | English, Sinhala, or Tamil | Yes |

3. Click **Save**.

> **Note:** Only active churches appear in the Receipt Entry screen.

### 5.3 Editing a Church

1. Click the **Edit** icon on the church row.
2. Modify fields as needed.
3. Click **Save**.

### 5.4 Deactivating a Church

1. Click the **Deactivate** button on the church row.
2. Confirm the action.

> **Warning:** Deactivating a church hides it from receipt entry but does **not** delete existing receipts for that church.

### 5.5 Filtering by Region

Use the **Region** dropdown filter at the top of the table to show only churches in a specific region.

### 5.6 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View churches | `church.menu.view` |
| Create church | `church.create` |
| Edit church | `church.update` |
| Activate/Deactivate | `church.delete` |

---

## 6. Receipt Entry

This is where you create weekly collection receipts for each church.

### 6.1 Creating a New Receipt

1. Open **Collections › Receipt**.
2. Fill in the receipt form:

**Basic Information:**

| Field | Description |
|-------|-------------|
| **Church** | Select from searchable dropdown (active churches only) |
| **Week Start Date** | The Monday (or configured identifier day) of the collection week |
| **Church Service Date** | The actual date of the church service |
| **Submitted By Name** | Name of the person who submitted the collection |

**Collection Items:**

| Field | Description |
|-------|-------------|
| **Offertory** | Amount and optional note |
| **Tithes** | Amount and optional note |
| **Other Donations** | Amount and optional note |
| **Total** | Automatically calculated (sum of all items) |

> **Note:** Amounts must be positive numbers with up to 2 decimal places (e.g., `1500.00`). At least one collection item must have an amount.

### 6.2 Late Submissions

If you are entering a receipt for a previous week:

1. The system will automatically detect this as a **late submission**.
2. A **Late Submission Reason** field will appear.
3. Enter the reason for the late submission.

> **Note:** Whether the late reason is required can be configured in Settings.

### 6.3 Saving and Printing

**Save & Print:**
1. Click **Save & Print**.
2. A confirmation dialog shows a summary of the receipt.
3. Click **Confirm** to create the receipt.
4. The receipt will be sent to the printer automatically.
5. Click **OK** to close, or **Preview** to see the receipt on screen.

**Save Only:**
1. Click **Save Only**.
2. Confirm the summary.
3. The receipt is saved without printing.

### 6.4 Receipt Numbering

Receipt numbers are automatically generated in the format:
`{PREFIX}{ZERO-PADDED-SEQUENCE}`

For example, if the prefix is `REC` and padding is 6, receipt numbers look like: `REC000001`, `REC000002`, etc.

Receipt numbers are guaranteed to be unique, even when multiple users create receipts simultaneously.

### 6.5 Duplicate Prevention

You cannot create a new receipt if:
- An **active** receipt already exists for the same church and same week
- You must first cancel the existing receipt before creating a new one

### 6.6 Clearing the Form

Click **Clear** to reset all fields and start over.

### 6.7 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View receipt entry | `receipt.entry.menu.view` |
| Print receipts | `receipt.print` |

---

## 7. Receipt History

View, search, cancel, reprint, and re-create past receipts.

### 7.1 Viewing Receipt History

1. Open **Collections › Receipt History**.
2. The table displays all receipts with:
   - Receipt Number
   - Church name
   - Region
   - Week date
   - Status (Active/Cancelled)
   - Total amount
   - Action buttons

### 7.2 Searching and Filtering

Use the filter options at the top of the table:

| Filter | Options |
|--------|---------|
| **Church** | Select specific church or ALL |
| **Region** | Select specific region or ALL |
| **Week Date** | Filter by specific week |
| **Status** | ALL, ACTIVE, CANCELLED |
| **Late Submission** | ALL, Late Only, On Time Only |
| **Receipt Type** | ALL, Re-Created Only, Original Only |
| **Print Status** | ALL, Printed Only, Not Printed Only |
| **Search Box** | Free text search |

Click **Search** to apply filters or **Clear** to reset all filters.

### 7.3 Viewing Receipt Details

Click the **View** button or **double-click** a receipt row to open the details dialog. The dialog has four tabs:

**Tab 1: Receipt Details**
- Receipt number, status, church, region, total
- Week date, service date, late flag, submitted by, issued by
- Items table showing each collection item

**Tab 2: Cancellation Details** (if cancelled)
- Cancellation reason
- Who cancelled and when
- Whether the receipt was within the cancellation window

**Tab 3: Print & SMS Details**
- Print status (Yes/No), printed at, printed by, print attempts
- SMS status, sent at, provider, error message

**Tab 4: Audit History**
- Chronological list of all actions performed on this receipt

### 7.4 Previewing a Receipt

1. Click the **Preview** button on the receipt row.
2. A dialog displays the receipt as it will appear when printed.
3. Use the scroll bars to view the full receipt.

### 7.5 Printing a Receipt

1. Click the **Print** button on the receipt row.
2. The receipt is sent to the configured printer.

> **Note:** The Print button is only available for receipts that:
> - Are **Active** (not cancelled)
> - Have **not** been printed before
> - You have the `receipt.print` permission

### 7.6 Sending SMS Notifications

1. Click the **Send SMS** button on the receipt row.
2. The SMS will be queued for delivery to the church's mobile number.

> **Note:** The SMS button is only available for receipts with a failed or pending SMS status.

### 7.7 Cancelling a Receipt

1. Click the **Cancel** button on the receipt row.
2. Enter a **cancellation reason** (required).
3. Confirm the cancellation.

**Important Rules:**
- Only **Active** receipts can be cancelled
- There is a **7-day cancellation window** from the receipt creation date
- Cancellation reasons are mandatory

### 7.8 Re-creating a Receipt (Correction Flow)

If you need to correct a cancelled receipt:

1. Click the **Re-create** button on the cancelled receipt row.
2. The **Receipt Entry** screen opens with all fields pre-filled from the cancelled receipt.
3. Make your corrections.
4. Save the new receipt.

> **Note:** The new receipt will be linked to the original cancelled receipt for tracking purposes.

### 7.9 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View receipt history | `receipt.menu.view` |
| Cancel receipts | `receipt.cancel` |
| Print receipts | `receipt.print` |

---

## 8. Submission Status

Track weekly submission progress across all churches and regions.

### 8.1 Viewing Submission Status

1. Open **Dashboard › Submission Status**.
2. The screen shows:
   - **Week selector** — Navigate between weeks using arrows or date picker
   - **KPI Summary cards** — Key metrics at a glance
   - **Collection Totals** — Total amounts by collection type
   - **Church submission table** — Detailed status for each church

### 8.2 Understanding the KPI Cards

| KPI | Description |
|-----|-------------|
| **Completed Regions** | Number of regions where all churches have submitted |
| **Submitted** | Number of churches that have submitted |
| **Pending** | Number of churches that have not submitted |
| **Late** | Number of late submissions |
| **Today's Receipts** | Receipts created today |
| **SMS Failed** | Number of failed SMS notifications |
| **Last Backup** | Status and date of last backup |

### 8.3 Understanding the Submission Table

Each row shows:

| Column | Description |
|--------|-------------|
| **Church Code** | Church identifier |
| **Church Name** | Full church name |
| **Region** | Associated region |
| **Total Amount** | Collection total for the week |
| **Status** | SUBMITTED (green), PENDING (yellow), or CANCELLED (red) |
| **Receipt No** | Receipt number (if submitted) |
| **Submitted Date** | When the receipt was created |

> **Note:** Late submissions are highlighted with a special indicator.

### 8.4 Filtering

- Use the **Region** dropdown to filter by region
- Use the **Church** dropdown to filter by church
- Use the **Status** dropdown to filter by status (ALL, SUBMITTED, PENDING, CANCELLED)

### 8.5 Viewing Receipt Details

**Double-click** or click **View** on any row to open the receipt details dialog.

---

## 9. Reports

Generate, export, and print various reports for analysis and record-keeping.

### 9.1 Accessing Reports

1. Open **Collections › Collection Reports** (SMS Delivery is under **Messaging › SMS Reports**; User Activity and Backup & Restore History are under **Administration › Admin Reports**).
2. The **Report Cards View** displays the 13 collection report types as clickable cards.

### 9.2 Report Types

#### Collection Reports

| # | Report Name | Description |
|---|-------------|-------------|
| 1 | **Weekly Church-wise Collection** | Shows each church's collection amounts for a specific week |
| 2 | **Weekly Region-wise Collection** | Shows each region's total collection for a specific week |
| 3 | **Church-wise Monthly Collection** | Monthly collection totals broken down by church |
| 4 | **Region-wise Monthly Collection** | Monthly collection totals broken down by region |
| 5 | **Church-wise Annual Collection** | Yearly collection totals broken down by church |
| 6 | **Region-wise Annual Collection** | Yearly collection totals broken down by region |

#### Submission Reports

| # | Report Name | Description |
|---|-------------|-------------|
| 7 | **Submission Status** | Overview of which churches have submitted for a given period |
| 8 | **Late Submission** | Lists all late submissions within a date range |
| 9 | **Church Progress** | Tracks submission consistency for each church |
| 10 | **Region Progress** | Tracks submission consistency for each region |

#### Operations Reports

| # | Report Name | Description |
|---|-------------|-------------|
| 11 | **Cancelled Receipt** | Lists all cancelled receipts with reasons |
| 12 | **Receipt Print Status** | Shows which receipts have been printed |
| 13 | **SMS Delivery** | SMS send/delivery status for all notifications |
| 14 | **User Activity** | Audit trail of all user actions in the system |
| 15 | **Backup & Restore History** | Log of all backup and restore operations |

### 9.3 Generating a Report

1. Click on a report card to select it.
2. The **filter panel** on the left updates to show relevant filters.
3. Set your filter criteria:

| Filter | Available For |
|--------|---------------|
| **Date Range** (From/To) | Most reports |
| **Week Date** | Weekly reports |
| **Region** | Church/Region reports |
| **Church** | Church-specific reports |
| **Status** | Status-related reports |
| **User** | User Activity report |
| **Collection Columns** | Collection reports (toggle Offertory/Tithes/Other/Grand Total) |

4. Click **Search** to load the report data.

### 9.4 Quick Date Ranges

For convenience, use these quick date range buttons:
- **This Week** — Current week only
- **Previous Week** — Last week only
- **This Month** — Current calendar month
- **Quarter** — Current quarter (3 months)
- **Year** — Current calendar year

### 9.5 Exporting Reports

**Export as PDF:**
1. Click **Export PDF**.
2. The PDF is generated using professional formatting.
3. The file is saved to the configured reports export folder.
4. A dialog shows the file path.

**Export as Excel:**
1. Click **Export Excel**.
2. The Excel file is generated with formatted data.
3. The file is saved to the configured reports export folder.
4. A dialog shows the file path.

**Print:**
1. Click **Print**.
2. A PDF is generated and sent to your system printer.

### 9.6 Understanding Report Tables

- **Columns** are dynamic based on the report type
- **Amount columns** are right-aligned for readability
- **Summary row** at the bottom shows totals for collection columns
- **Double-click** a row to view receipt details (for receipt-based reports)

### 9.7 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View reports | `report.menu.view` |

---

## 10. User Management

Create, edit, and manage user accounts.

### 10.1 Viewing Users

1. Open **Administration › Users**.
2. The table displays:
   - Username
   - Full Name
   - Role
   - Status (Active/Inactive)
   - Force Password Change (Yes/No)
   - Created At

### 10.2 Creating a New User

1. Click the **+ Create User** button.
2. Fill in the form:

| Field | Description | Required |
|-------|-------------|----------|
| **Username** | Unique login identifier | Yes |
| **Full Name** | User's full name | Yes |
| **Role** | Select from dropdown | Yes |
| **Status** | Active or Inactive | Yes |
| **Temporary Password** | Initial password for first login | Yes |
| **Force Password Change** | Require password change on first login | Recommended: Yes |

3. Click **Save**.

> **Note:** New users should log in with the temporary password and change it immediately.

### 10.3 Editing a User

1. Click the **Edit** icon on the user row.
2. Modify fields (cannot change username or password here).
3. Click **Save**.

### 10.4 Resetting a User's Password

1. Click the **Reset Password** icon on the user row.
2. Enter a new **Temporary Password**.
3. Check **Force Password Change** if the user should change the password on next login.
4. Click **Save**.

### 10.5 Deactivating a User

1. Click the **Deactivate** button on the user row.
2. Confirm the action.

> **Warning:** A deactivated user **cannot sign in** to the system. Their data is preserved.

### 10.6 Searching Users

Use the search box to filter by username, full name, or role name.

### 10.7 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View users | `user.menu.view` |
| Create user | `user.create` |
| Edit user | `user.update` |
| Deactivate user | `user.delete` |

---

## 11. Roles & Permissions

Define roles and assign specific permissions to control what users can do.

### 11.1 Understanding Permissions

Permissions control access to features. Each permission follows a naming pattern:
`{module}.{action}`

Examples:
- `receipt.entry.menu.view` — Can view the Receipt Entry screen
- `receipt.cancel` — Can cancel receipts
- `region.create` — Can create new regions

### 11.2 Viewing Roles

1. Open **Administration › Roles & Permissions**.
2. The left panel shows all roles with their status.

### 11.3 Creating a New Role

1. Click the **+ Create Role** button.
2. Enter a **Role Name** (e.g., "Treasurer" or "Region Admin").
3. Click **Save**.

### 11.4 Assigning Permissions

1. Select a role from the list.
2. The right panel shows all permissions grouped by module.
3. Check the boxes for permissions you want to assign.
4. Click **Save Permissions**.

**Permission Groups:**

| Module | Available Permissions |
|--------|---------------------|
| **Regions** | View, Create, Update, Delete |
| **Churches** | View, Create, Update, Delete |
| **Receipts** | Entry View, History View, Print, Cancel |
| **Reports** | View |
| **User Management** | View, Create, Update, Delete |
| **Roles & Permissions** | View |
| **Backup** | View, Create, Restore, Settings Manage |
| **Activity Logs** | View |
| **SMS Logs** | View, Resend |
| **Settings** | View |

### 11.5 The Admin Role

The **Admin** role has a special property: users with the Admin role **bypass all permission checks**. This means:
- Admins can access all features regardless of individual permission settings
- Admins can manage other users and roles
- Use the Admin role sparingly for trusted administrators only

### 11.6 Deactivating a Role

1. Select the role.
2. Click **Deactivate Role**.
3. Confirm the action.

> **Warning:** Users with a deactivated role will **lose access** to features until the role is reactivated.

### 11.7 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View roles | `role.menu.view` |

---

## 12. Backup & Restore

Protect your data with regular backups and restore from backups when needed.

> **Important:** Backup & Restore is only available on the **primary (server) machine**. If you are on a secondary client, you will see a notice indicating this feature is unavailable.

### 12.1 Creating a Manual Backup

1. Open **Administration › Backup & Restore**.
2. Select a **backup folder** using the browse button.
3. Click **Backup Now**.
4. Wait for the backup to complete.
5. A success message confirms the backup file was created.

### 12.2 Setting Up Automatic Backups

1. Go to the **Automatic Backup Schedules** section.
2. Click **Add Schedule**.
3. Configure:
   - **Schedule Name** — A descriptive name (e.g., "Daily Evening Backup")
   - **Backup Time** — Select hour, minute, and AM/PM
   - **Enabled** — Check to activate the schedule
4. Click **Save**.
5. The schedule is added to the list.

**Managing Schedules:**
- **Toggle** the switch to enable/disable a schedule
- **Edit** a schedule by selecting it and modifying the time
- **Delete** a schedule by selecting it and clicking Delete

### 12.3 Backup Settings

Configure these settings in the **Backup Settings** section:

| Setting | Description |
|---------|-------------|
| **Auto Backup Folder** | Where backup files are stored |
| **Retention Days** | How many days to keep auto backups (older ones are deleted) |
| **mysqldump Path** | Full path to the `mysqldump.exe` tool |
| **MySQL Client Path** | Full path to the `mysql.exe` tool |

> **Note:** The `mysqldump` and `mysql` tools are typically found in the MySQL installation directory (e.g., `C:\Program Files\MySQL\MySQL Server 9.7\bin\`).

Click **Save Settings** after making changes.

### 12.4 Restoring from a Backup

1. Go to the **Restore** section.
2. Click **Browse** to select a `.sql` backup file.
3. **Type the confirmation text** as shown in the dialog (safety measure).
4. Click **Restore**.

> **Warning:** Restore will **replace all current database data** with the backup contents. A pre-restore backup will be created automatically before the restore begins.

After restore completes, the application will **restart automatically**.

### 12.5 Viewing Backup History

The **Backup History** table shows:
- Date and time of backup
- Type (Manual, Auto, Pre-Restore)
- File name and size
- Status (Success/Failed)
- Created by (user)
- Error message (if any)

Use the filters to search by date range, type, or status.

### 12.6 Viewing Restore History

The **Restore History** table shows:
- Date and time of restore
- File name
- Status (Success/Failed)
- Restored by (user)
- Error message (if any)

### 12.7 Generating Windows Task Scheduler Script

For OS-level scheduling outside the application:

1. Select a backup schedule.
2. Click **Generate Script**.
3. A `.bat` file is created.
4. Use **Windows Task Scheduler** to run this file daily at the selected backup time.

### 12.8 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View backup screen | `backup.menu.view` |
| Create backup | `backup.create` |
| Restore backup | `backup.restore` |
| Manage backup settings | `backup.settings.manage` |
| View backup history | `backup.view` |

---

## 13. Activity Logs

View a complete audit trail of all actions performed in the system.

### 13.1 Viewing Activity Logs

1. Open **Administration › Activity Logs**.
2. The table displays the most recent 100 entries with:
   - Date and Time
   - User who performed the action
   - Action type (CREATE, UPDATE, DELETE, LOGIN, LOGOUT, etc.)
   - Module affected
   - Record ID
   - Machine name
   - Description

### 13.2 Searching Activity Logs

Use the filters to narrow down the results:

| Filter | Description |
|--------|-------------|
| **Date Range** | Start and end dates |
| **User** | Specific user or ALL USERS |
| **Action** | Specific action or ALL ACTIONS |
| **Module** | Specific module or ALL MODULES |
| **Keyword** | Free text search in descriptions |

Click **Search** to apply filters or **Refresh** to load the latest 100 entries.

### 13.3 Viewing Log Details

1. **Double-click** a log entry or click the **View Details** button.
2. A dialog shows the full details including:
   - IP Address
   - Old Value (formatted key-value pairs showing previous state)
   - New Value (formatted key-value pairs showing new state)

### 13.4 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View activity logs | `activity.menu.view` |

---

## 14. SMS Logs

Monitor SMS notification delivery and resend failed messages.

### 14.1 Viewing SMS Logs

1. Open **Messaging › SMS Logs**.
2. The table displays:
   - Date and Time
   - Receipt Number
   - Church Code
   - Mobile Number
   - Message content
   - Send Status (badge)
   - Delivery Status (badge)

### 14.2 Understanding SMS Statuses

**Send Status:**

| Status | Meaning |
|--------|---------|
| **QUEUED** | Waiting to be sent |
| **SENDING** | Currently being sent |
| **SENT** | Sent successfully |
| **FAILED** | Failed to send |
| **SKIPPED** | Skipped (e.g., no mobile number) |

**Delivery Status:**

| Status | Meaning |
|--------|---------|
| **UNKNOWN** | Delivery status not available |
| **PENDING** | Waiting for delivery confirmation |
| **DELIVERED** | Successfully delivered to recipient's phone |
| **FAILED** | Delivery failed |
| **NOT_SUPPORTED** | Delivery reports not supported by the provider |

### 14.3 Searching SMS Logs

| Filter | Description |
|--------|-------------|
| **Date Range** | Start and end dates |
| **Church** | Specific church or ALL |
| **Send Status** | Filter by send status |
| **Delivery Status** | Filter by delivery status |
| **Type** | *Receipt* (receipt notifications) or *Official* (messages from Send Message) |
| **Search Box** | Free text search |

### 14.4 Resending Failed SMS

1. Find a failed SMS in the log.
2. Click the **Resend** button.
3. Enter a **reason** for the resend (required).
4. The SMS will be queued for re-delivery.

> **Note:** Resending is only available for SMS logs within **7 days** of the original send attempt.

### 14.5 Viewing SMS Details

1. **Double-click** an SMS log entry.
2. A dialog shows:
   - **Send Details** — Status, attempts, timestamps, error messages
   - **SMS Details** — Full message text, raw modem response, delivery report

### 14.6 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View SMS logs | `sms.menu.view` |
| Resend SMS | `sms.resend` |
| Open Send Message | `sms.official.menu.view` |
| Send official messages | `sms.official.send` |

### 14.7 Sending Official Messages

Use **Send Message** (**Messaging › Send Message**) to send an official notice to branches.

1. **Choose branches.** Pick a region (or *All regions*) and optionally search by code or name. Tick branches one by one, or click **Select all shown** to tick every branch the list shows. Ticks are kept when you change the region, so you can combine regions. **Clear** removes all ticks.
2. Branches marked *No mobile number* or *Invalid number* cannot be ticked. Fix the number on the branch first (Churches → Edit → SMS Mobile Number).
3. **Type the message** in English. The counter shows the length and how many SMS parts it uses (1 part up to 160 characters; longer messages use up to 3 parts / 459 characters). The footer from Settings is added automatically and is counted. Sinhala, Tamil and emoji cannot be sent — the counter names the first character that is not allowed.
4. The line below shows the **total SMS** that will be sent (branches × parts). Each part is charged as one SMS.
5. Click **Send** and confirm. The messages are queued and sent by the server machine one after another. Track them in **SMS Logs** — set **Type** to *Official*.

**Note:** if a long message fails part-way and is resent, the branch may receive the first part(s) twice.

---

## 15 Settings

Configure system-wide settings for your organization.

### 15.1 Accessing Settings

1. Open **Administration › Settings**.
2. The settings screen has four tabs: General, Receipt, SMS, and System.

### 15.2 General Settings

Configure your organization's information:

| Setting | Description |
|---------|-------------|
| **Organization Name** | Your church organization's name |
| **Address** | Full address |
| **Phone** | Contact phone number |

Click **Save** after making changes.

### 15.3 Receipt Settings

Configure receipt behavior:

| Setting | Description | Default |
|---------|-------------|---------|
| **Receipt Number Prefix** | Text before the receipt number (e.g., `REC`) | REC |
| **Sequence Padding** | Number of digits in receipt number (e.g., `6` gives `REC000001`) | 6 |
| **Allow Back-Week** | Allow entering receipts for previous weeks | Checked |
| **Late Reason Required** | Require reason for late submissions | Unchecked |
| **Default Language** | Default language for new receipts (English/Sinhala/Tamil) | English |
| **Week Identifier Day** | Which day starts a new week (e.g., Monday) | Monday |

Click **Save** after making changes.

### 15.4 SMS Settings

Configure SMS notification:

| Setting | Description |
|---------|-------------|
| **SMS Enabled** | Turn SMS notifications on/off |
| **Gateway Type** | `MOCK` (for testing) or `SIM_DONGLE` (real SMS) |
| **Retry Enabled** | Automatically retry failed SMS |
| **Max Retry Attempts** | Number of retry attempts (if retry enabled) |
| **COM Port** | Serial port for the SMS modem |
| **Baud Rate** | Communication speed (typically 9600 or 115200) |
| **Official message footer** | Text added on its own line to every official message (e.g. `- Head Office`). Up to 100 English characters; leave blank for none. It counts toward the message length. |

**Testing the Modem:**
1. Select the COM port.
2. Click **Detect COM Ports** to scan for available ports.
3. Click **Test AT Command** to verify modem communication.

**Sending a Test SMS:**
1. Enter a **mobile number**.
2. Enter a **test message**.
3. Click **Send Test SMS**.
4. Check the result message.

**Open Send Message** opens the Send Message screen (see 14.7).

Click **Save** after making changes.

### 15.5 System Settings

Configure system preferences:

| Setting | Description |
|---------|-------------|
| **Date Format** | How dates are displayed throughout the system |
| **Time Format** | 12-hour or 24-hour time display |
| **Report Export Folder** | Where exported reports are saved |
| **Receipt PDF Export Folder** | Where receipt PDFs are saved |
| **Continuous Paper Mode** | Optimized for dot-matrix continuous paper printing |
| **PDF Report Charts** | Include charts in PDF report exports |
| **Theme** | Visual theme: ORCHID (default), LIGHT, or DARK |

Click **Save** after making changes. The theme is applied immediately.

### 15.6 Required Permissions

| Action | Permission Required |
|--------|-------------------|
| View/Change settings | `settings.menu.view` |

---

## 16. My Profile

Manage your personal profile and change your password.

### 16.1 Viewing Your Profile

1. Click **My Profile** in the header.
2. The screen shows your account information:
   - **Username** (read-only)
   - **Role** (read-only)
   - **Status** (read-only)
   - **Full Name** (editable)
   - **Email** (editable)
   - **Mobile Number** (editable)
   - **Profile Picture** (uploadable)
   - **Open after login** — the screen that opens when you sign in (see 3.4)

### 16.2 Updating Your Profile

1. Edit your name, email, or mobile number.
2. Click **Save Profile**.

### 16.3 Uploading a Profile Picture

1. Click **Upload Picture**.
2. Select a JPG or PNG image file.
3. The image is cropped to a circular preview.
4. Click **Save Profile** to apply the change.

> **Note:** If no picture is uploaded, your initials will be displayed instead.

### 16.4 Changing Your Password

1. Click **Change Password**.
2. Enter your **Current Password**.
3. Enter and confirm your **New Password**.
4. Click **Change Password**.

> **Note:** Password requirements are enforced by the system. Choose a strong password that you haven't used before.

### 16.5 Required Permissions

All authenticated users can access My Profile.

---

## 17. Troubleshooting

### 17.1 Login Issues

**Problem:** "Invalid username or password"
- Verify your username and password are correct
- Check that Caps Lock is not accidentally on
- Contact your administrator to reset your password

**Problem:** "Password change required before accessing the dashboard"
- You must change your password before using the system
- Follow the Force Password Change screen instructions

### 17.2 Database Connection Issues

**Problem:** "Cannot connect to the database"
1. Verify MySQL is running on the server
2. Check the database host and port in `application.properties`
3. Verify the username and password are correct
4. Check that the database `church_management_system` exists
5. Verify network connectivity between client and server

**Problem:** "Access denied -- check username and password"
- The database credentials are incorrect
- Verify `db.username` and `db.password` in `application.properties`

**Problem:** "The configured database does not exist"
- Run the Database Setup Wizard to create the database
- Or create the database manually in MySQL

### 17.3 SMS Issues

**Problem:** "No COM ports found"
1. Ensure the SIM dongle is plugged in
2. Install the required USB drivers
3. Try a different USB port
4. Click **Detect COM Ports** again

**Problem:** "Modem not responding"
1. Verify the correct COM port is selected in Settings
2. Check the baud rate matches your modem (usually 9600)
3. Ensure no other application is using the COM port
4. Test with **Test AT Command** in Settings

**Problem:** "SMS Failed"
1. Check the SMS Logs for error details
2. Verify the SIM card has credit
3. Verify the mobile number format is correct
4. Try resending the SMS from SMS Logs

### 17.4 Backup Issues

**Problem:** "Backup failed"
1. Verify the backup folder exists and is writable
2. Check that `mysqldump` path is correct in Backup Settings
3. Verify MySQL credentials have backup privileges
4. Check available disk space

**Problem:** "Restore failed"
1. Verify the backup file is a valid SQL file
2. Check that MySQL is running
3. Verify the MySQL user has sufficient privileges
4. Check the error message in Restore History

### 17.5 Printing Issues

**Problem:** Receipts not printing
1. Verify the printer is connected and powered on
2. Check the printer is set as the system default
3. Ensure the receipt template is configured correctly
4. Check the **Continuous Paper Mode** setting for dot-matrix printers

### 17.6 Application Issues

**Problem:** Application freezes or becomes unresponsive
1. Close the application (Task Manager if needed)
2. Restart the application
3. Check the logs folder for error details

**Problem:** "Access denied" when opening a module
- Your role does not have permission for that module
- Contact your administrator to request access

### 17.7 Common Error Messages

| Error Message | Solution |
|---------------|----------|
| "Please sign in to continue" | Your session expired; log in again |
| "You do not have permission" | Contact your administrator for role/permission changes |
| "Backup & Restore is only available on the server" | You are on a secondary client; use the server machine |
| "Receipt already exists for this church and week" | Cancel the existing receipt first, then create a new one |
| "Cancellation window expired" | Receipts can only be cancelled within 7 days |
| "SMS resend period has expired" | SMS can only be resent within 7 days |

---

## 18. Glossary

| Term | Definition |
|------|------------|
| **Church** | An individual congregation within a region |
| **Collection** | The total monetary offerings gathered during a church service |
| **Offertory** | General offerings collected during the service |
| **Tithes** | Regular tithing contributions |
| **Other Donations** | Any additional donations (building fund, special collections, etc.) |
| **Receipt** | A numbered record of a church's weekly collection submission |
| **Region** | A geographic grouping of churches |
| **Late Submission** | A receipt entered after the configured week identifier day |
| **Correction** | A new receipt created to replace a cancelled receipt |
| **SMS Notification** | An automated text message sent to the church's authorized person |
| **SIM Dongle** | A USB device with a SIM card used to send SMS messages |
| **AT Command** | Commands sent to the modem to control SMS sending |
| **COM Port** | Serial communication port for the SMS modem |
| **Dot-Matrix Printer** | Impact printer commonly used for receipt printing |
| **Continuous Paper** | Paper fed through a tractor-feed mechanism (no page breaks) |
| **Flyway** | Database migration tool used to manage schema changes |
| **mysqldump** | MySQL command-line tool for creating database backups |
| **Primary Machine** | The server machine that runs migrations and scheduled backups |
| **Secondary Machine** | A client machine that connects to the primary database |
| **KPI** | Key Performance Indicator — important metrics displayed on the dashboard |

---

## Support

For technical support or questions about the Church Management System, please contact your system administrator or refer to the installation documentation included with the software.

**Developed By:** Janaka Perera  
**Contact Number:** 0779156683  
**Email:** janakaudayangaperera@gmail.com

---

*Document Version: 1.1.0 — September 2026*

---

## 19. Finance

The Finance module keeps the church's double-entry books. Open it from **Home › Finance** (Ctrl+5).

### 19.1 Key ideas

- Every financial event is a **journal entry**: debits must equal credits.
- An entry is first a **draft** (can be edited or deleted). When it is **posted** it gets a number such as **JE-2027-00001** and can never be changed — mistakes are corrected with a **reversal**.
- Income, expense and Accumulated Fund lines carry a **category** (A, B, C …) so each category's income and spending can be reported. Cash and bank lines do not.
- The books start on the **go-live date** (1 January 2027). Entries cannot be dated earlier.

### 19.2 Chart of Accounts

**Finance › Chart of Accounts** shows all accounts grouped as Assets (1xxx), Liabilities (2xxx), Equity (3xxx), Income (4xxx) and Expenses (5xxx), with each account's balance.

To add an account (needs *Manage accounts*): select the group (e.g. **1200 Bank Accounts**), click **Add account**, enter a 4-digit code inside the group's range shown in the dialog (e.g. 1201) and a name (e.g. *BOC Current Account*), then **Save**. Tick *This is a group* to create a new group (code ending in 00, directly under a main group).

Accounts that have been used cannot change code or group and cannot be deleted — **Deactivate** them instead. An account can only be deactivated when:

- its balance is zero (move any balance to another account with a journal entry first),
- it is not used on any draft entry, and
- it has no entries in an accounting year that is still open (deactivate it after that year is closed).

A group can be deactivated once every account under it is inactive. Cash in Hand, Accumulated Fund, Tithes, Offerings and Other Donations are system accounts and stay active.

### 19.3 General Journal

**Finance › General Journal** lists entries by date, status and source. Click **New entry**, choose the date, type a narration and add lines: account, debit **or** credit, category (required for income, expense and Accumulated Fund lines), optional branch and memo. The bottom line shows the totals and warns *Out of balance by …* until debits equal credits.

- **Save draft** keeps the entry for later.
- **Post** records it permanently and gives it a number.
- **Reverse…** (on a posted entry) asks for a reason and posts a new entry with debits and credits swapped.

**Approval:** if *Require approval before posting* is ticked in **Settings › Finance**, the person who prepared an entry cannot post it; someone with *Approve finance entries* must post it.

### 19.4 Opening Balances

**Finance › Opening Balances** records the position on the go-live date: the balance of each asset and liability account (cash, bank, buildings, loans …) and the **Accumulated Fund for each category**. The totals must balance (Assets − Liabilities = Funds). **Save draft**, then **Post**. To correct posted opening balances, use **Reverse to correct** and enter them again.

### 19.5 Account Ledger

**Finance › Account Ledger** shows, for one account and date range, the opening balance, every posted line with a running balance, and the closing balance. Double-click a line to open its entry.

### 19.6 Settings and permissions

**Settings › Finance:** go-live date (locked after the first posting) and *Require approval before posting*.

| Action | Permission |
|--------|------------|
| Open Finance and view | `finance.menu.view` |
| Prepare entries and opening balances | `finance.entry` |
| Post when approval is required; reverse | `finance.approve` |
| Add, edit, deactivate accounts | `finance.accounts.manage` |
| Close accounting years (later release) | `finance.year.close` |
