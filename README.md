# Smart Assessment (NetBeans project)

LAN-based exam application with three separate question types - **MCQ, Written and Coding** - and integrated anti-cheating.
Java Swing / JFrame, OOP only - **no SQL or database** (data is stored in plain files).

## Open in NetBeans
1. Extract the zip.
2. NetBeans > **File > Open Project...** > select the `SmartAssessmentNetBeans` folder.
3. Right-click the project > **Run** (main class `smartassessment.Main`). Use JDK 11 or newer.

If NetBeans reports missing build files: *File > New Project > Java with Existing Sources*,
choose this folder and set `src` as the source folder.

## Project structure
```
SmartAssessmentNetBeans
├── build.xml, README.md, nbproject/, test/
└── src/smartassessment
    ├── Main.java
    ├── model     Student, Question, Exam, Submission, AssessmentResult, StudentSession
    ├── network   Protocol, Message, ExamClient, ExamServer
    ├── service   ExamRepository (files), ExamService (rules, marking, generator), CodeRunner
    ├── ui        MainFrame, TeacherFrame, StudentLoginFrame, ExamFrame,
    │             QuestionManagerFrame, SyntaxEditor
    └── util      Theme
```

## Teacher
1. Start the app > **I am a Teacher** (password `teacher123`, change it in `MainFrame.java`).
2. **Exam Setup**: title, exam code, duration, optional allowed rolls. Click **Manage Questions**:
   * choose the type: **MCQ** (option items, correct answer), **Written** (student types a text
     answer) or **Coding** (student writes code in the editor and can Run it); each has marks and an
     optional picture; edit, remove, re-order;
   * **Generate questions automatically...**: type how many MCQ, Written and Coding questions you
     want (e.g. 600 + 200 + 200 = **1000 questions**), tick "random order" to mix the types, choose
     the marks and add / replace. All MCQ answers are calculated by the program, no duplicates
     (tested up to 2000);
   * **Import from file (txt / csv / pdf)...**: no need to type line by line (formats below);
   * **Export CSV...** saves all questions with correct answers; **Save sample files...**
     writes `sample_questions.txt` and `sample_questions.csv` to copy the format from.
   Then **Save exam**.
3. **Live Monitor**: press **1. Start Server & Exam**. The server starts and the exam starts at the
   same moment (the timer begins). Tell students the IP shown. Press **2. End Exam** when finished.
4. **Another subject on the same server**: after the exam has ended the first button changes to
   **1. Start Another Exam**. Go to *Exam Setup*, **Load selected** (or create) the other exam
   (a different exam code), come back to *Live Monitor* and press **1. Start Another Exam**.
5. **A student missed the exam?** After it has ended press **3. Restart this Exam**: same
   questions, new timer, optional list of rolls allowed. Students who already submitted stay
   locked out. This also works on another day: load the exam and press Start (earlier
   submissions are remembered on disk).
6. **Submissions & Marks**: MCQ is auto-marked; use *Give marks (written / coding)* for the other
   two parts; view written answers, coding answers, MCQ answer sheet, replay how the code was
   written, anti-cheat report, export CSV.

## Question file formats (Manage Questions > Import)
**TXT / PDF** (a PDF is read as text, so use the same layout):
```
Q: Which keyword is used to inherit a class in Java?
A) implements
B) extends
C) inherits
D) super
Answer: B
Marks: 1

W: Define polymorphism and explain it with an example.
Marks: 5

C: Write a program that prints the sum of the numbers from 1 to 100.
Marks: 10
```
`Q:` (or `1.` / `Q1.`) starts an **MCQ**, `W:` a **Written** question, `C:` a **Coding** question.
Options can be `A)` `A.` or `a)`. Mark the right option with `Answer: B` or by putting `*` after it.
Marks lines are optional (the dialog asks for default marks). Lines starting with `#` are ignored.
Items that cannot be understood are skipped and listed after the import.

**CSV** (open in Excel, save as CSV):
`Type,Marks,Question,A,B,C,D,E,F,G,H,I,J,Correct` - Type is `MCQ`, `WRITTEN` or `CODING`.
Scanned (picture) or password-protected PDFs cannot be read - copy the text into a .txt file.

## Student
**I am a Student** > Name, Batch, Exam Roll, Exam Code, Teacher IP > Next.
The exam has the tabs **MCQ** (tick the answers), **Written** (type the answers) and **Coding**
(editor, language, Run). Only the tabs that exist in the exam are shown. Press Submit
(auto-submit when time ends). Teacher PC must allow TCP port **5555**.

## Anti-cheat
Copy/cut/paste blocked, clipboard cleared, focus loss timed, second monitor detected,
forbidden apps detected, large text insertions detected, code snapshots every 10 s (replay),
warning limit -> auto-submit, reconnect with answers restored.

## Notes
* Every student receives **all** questions of an exam - for a real exam generate a number that fits the time.
* Data folder: `SmartExamData` (next to where the program is started). Delete the old
  `SmartExamData` folder if you used the previous version (file format changed).
