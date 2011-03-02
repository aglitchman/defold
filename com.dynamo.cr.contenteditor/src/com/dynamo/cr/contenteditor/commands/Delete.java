package com.dynamo.cr.contenteditor.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

import com.dynamo.cr.contenteditor.editors.IEditor;
import com.dynamo.cr.contenteditor.operations.DeleteOperation;
import com.dynamo.cr.contenteditor.scene.Node;

public class Delete extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (editor instanceof IEditor) {
            Node[] selectedNodes = ((IEditor) editor).getSelectedNodes();
            if (selectedNodes.length >= 0) {
                DeleteOperation op = new DeleteOperation(selectedNodes);
                ((IEditor) editor).executeOperation(op);
            }
        }
        return null;
    }
}
