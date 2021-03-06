package com.boxysystems.scriptmonkey.intellij.ui;

import com.boxysystems.scriptmonkey.intellij.Constants;
import com.boxysystems.scriptmonkey.intellij.ScriptMonkeySettings;
import com.boxysystems.scriptmonkey.intellij.action.CloseScriptConsoleAction;
import com.boxysystems.scriptmonkey.intellij.action.RerunScriptAction;
import com.boxysystems.scriptmonkey.intellij.action.StopScriptAction;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public class ScriptShellPanel extends JPanel implements ScriptProcessorPrinter {
    private ShellCommandProcessor shellCommandProcessor;
    private AnAction[] actions;
    private EditorImpl editor;

    public boolean isScriptShell() {
        return shellCommandProcessor.isCommandShell();
    }

    public ScriptShellPanel(ShellCommandProcessor cmdProc, AnAction actions[]) {
        this.shellCommandProcessor = cmdProc;
        this.actions = actions;

        setLayout(new BorderLayout());

        final DefaultActionGroup toolbarGroup = new DefaultActionGroup();

        for (int i = 0; i < actions.length; i++) {
            AnAction action = actions[i];
            toolbarGroup.add(action);
        }

        final ActionManager actionManager = ActionManager.getInstance();
        final ActionToolbar toolbar = actionManager.createActionToolbar(Constants.PLUGIN_ID, toolbarGroup, false);

        add(toolbar.getComponent(), BorderLayout.WEST);

        // use IDEA's editor for the console
        Language language = Language.findLanguageByID("JS");
        FileType fileType = language != null ? language.getAssociatedFileType() : null;
        boolean foundType = fileType != null;
        FileType fileTypeHighlighOnly = null;

        if (!foundType) {
            fileType = StdFileTypes.PLAIN_TEXT;
            fileTypeHighlighOnly = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(".js");
        }

        FileType highlighterFileType = foundType || fileTypeHighlighOnly == null ? fileType : fileTypeHighlighOnly;

        Project project = shellCommandProcessor.getProject();
        assert project != null;

        CommandShellDocument myDocument = new CommandShellDocument((DocumentEx) EditorFactory.getInstance().createDocument(""), this);

        editor = (EditorImpl) (cmdProc.isCommandShell() ?
                EditorFactory.getInstance().createEditor(myDocument, project) :
                EditorFactory.getInstance().createViewer(myDocument, project));

        editor.setHighlighter(HighlighterFactory.createHighlighter(project, highlighterFileType));

        editor.getDocument().addDocumentListener(new CommandShellDocumentListener(this));

        editor.getSettings().setUseTabCharacter(false);

        add(editor.getComponent());

        if (shellCommandProcessor.isCommandShell()) {
            clear();
        }
    }

    public void disposeComponent() {
        // release the editor
        final EditorImpl thisEditor = editor;
        editor = null;

        remove(thisEditor.getComponent());

        final Application application = ApplicationManager.getApplication();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (!thisEditor.isDisposed()) {
                    EditorFactory.getInstance().releaseEditor(thisEditor);
                }
            }
        };

        if (application.isUnitTestMode() || application.isDispatchThread()) {
            runnable.run();
        }
        else {
            application.invokeLater(runnable);
        }

        shellCommandProcessor = null;
        actions = null;
    }

    public Project getProject() {
        return shellCommandProcessor.getProject();
    }

    public void clear() {
        clear(true);
    }

    public void clear(boolean prompt) {
        CommandShellDocument d = getDocument();
        d.beginUpdate();
        d.clear();
        if (prompt) {
            printPrompt();
        }
        d.endUpdate();
        // this is done automatically after bulk update
        // setMark();
        requestFocus();
    }

    @Override
    public void requestFocus() {
        editor.getContentComponent().requestFocus();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    public CommandShellDocument getDocument() {
        return (CommandShellDocument) editor.getDocument();
    }

    public void print(String s) {
        CommandShellDocument d = (CommandShellDocument) editor.getDocument();
        d.beginUpdate();
        d.appendString(s);
        d.endUpdate();
    }

    @Override
    public void println(final String s) {
        CommandShellDocument d = getDocument();
        d.beginUpdate();
        d.appendString(s + "\n");
        d.endUpdate();
    }

    @Override
    public void progressln(String s) {
        CommandShellDocument d = getDocument();
        d.beginUpdate();
        d.appendString(s + "\n");
        d.endUpdateAndFlush();
    }

    @Override
    public void startProgress() {
        CommandShellDocument d = getDocument();
        d.beginUpdate();
    }

    @Override
    public void endProgress() {
        CommandShellDocument d = getDocument();
        d.endUpdateAndFlush();
    }

    @Override
    public boolean hadOutput() {
        return getDocument().hadOutput();
    }

    public void printPrompt() {
        if (shellCommandProcessor.isCommandShell()) {
            print(getPrompt());
        }
    }

    private String getPrompt() {
        return shellCommandProcessor.getPrompt();
    }

    public Object executeCommand(String cmd, int lineOffset, int firstLineColumnOffset) {
        getDocument().beginUpdate();
        StopScriptAction stopScriptAction = getStopScriptAction();
        stopScriptAction.setEnabled(true);
        Object result = shellCommandProcessor.executeCommand(cmd, lineOffset, firstLineColumnOffset, stopScriptAction, this);
        stopScriptAction.setEnabled(false);
        getDocument().endUpdate();
        return result;
    }

    public ShellCommandProcessor getShellCommandProcessor() {
        return shellCommandProcessor;
    }

    public EditorImpl getEditor() {
        return editor;
    }

    public void applySettings(ScriptMonkeySettings settings) {
        //editor.setBackgroundColor(settings.getCommandShellBackgroundColor());
        //editor.setForeground(settings.getCommandShellForegroundColor());
    }

    public void toggleActions() {
        for (int i = 0; i < actions.length; i++) {
            AnAction action = actions[i];
            if (action instanceof RerunScriptAction) {
                RerunScriptAction rerunScriptAction = (RerunScriptAction) action;
                rerunScriptAction.setEnabled(!rerunScriptAction.isEnabled());
            }

            if (action instanceof StopScriptAction) {
                StopScriptAction stopScriptAction = (StopScriptAction) action;
                stopScriptAction.setEnabled(!stopScriptAction.isEnabled());
            }

            if (action instanceof CloseScriptConsoleAction) {
                CloseScriptConsoleAction closeSctiptConsoleAction = (CloseScriptConsoleAction) action;
                closeSctiptConsoleAction.setEnabled(!closeSctiptConsoleAction.isEnabled());
            }
        }
    }

    public StopScriptAction getStopScriptAction() {
        for (int i = 0; i < actions.length; i++) {
            AnAction action = actions[i];
            if (action instanceof StopScriptAction) {
                return (StopScriptAction) action;
            }
        }
        return null;
    }
}
