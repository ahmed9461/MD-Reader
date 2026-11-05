package app.mdreader.mobile;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import java.util.function.Consumer;

/** Editor-owned actions in the floating toolbar anchored to Android's selection handles. */
final class EditorSelectionMenu implements ActionMode.Callback {
    private interface Transform {
        MarkdownTransforms.Result apply(String text, int start, int end);
    }

    enum Format {
        HIGHLIGHT("تظليل", SafeHtmlEditorTools::highlight),
        BOLD("عريض", (t, a, b) -> MarkdownTransforms.wrap(t, a, b, "**", "**", "نص")),
        ITALIC("مائل", (t, a, b) -> MarkdownTransforms.wrap(t, a, b, "*", "*", "نص")),
        UNDERLINE("تسطير", SafeHtmlEditorTools::underline);

        private final int id = View.generateViewId();
        private final String label;
        private final Transform transform;

        Format(String label, Transform transform) {
            this.label = label;
            this.transform = transform;
        }

        MarkdownTransforms.Result applyTo(EditText editor) {
            int a = editor.getSelectionStart();
            int b = editor.getSelectionEnd();
            return transform.apply(editor.getText().toString(), Math.min(a, b), Math.max(a, b));
        }
    }

    private static final int FORMAT_GROUP = View.generateViewId();
    private final EditText editor;
    private final Consumer<MarkdownTransforms.Result> apply;

    EditorSelectionMenu(EditText editor, Consumer<MarkdownTransforms.Result> apply) {
        this.editor = editor;
        this.apply = apply;
    }

    @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        for (Format format : Format.values()) {
            menu.add(FORMAT_GROUP, format.id, format.ordinal(), format.label)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return onPrepareActionMode(mode, menu);
    }

    @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Android can refresh smart actions and Select All as the handles move.
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getGroupId() == FORMAT_GROUP) continue;
            int id = item.getItemId();
            if (id == android.R.id.cut || id == android.R.id.copy || id == android.R.id.paste
                    || id == android.R.id.pasteAsPlainText || id == android.R.id.selectAll) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            } else {
                item.setVisible(false);
            }
        }
        return true;
    }

    @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getGroupId() != FORMAT_GROUP) return false;
        for (Format format : Format.values()) {
            if (item.getItemId() == format.id && editor.hasSelection()) {
                // Finishing the native mode collapses the selection.
                MarkdownTransforms.Result result = format.applyTo(editor);
                mode.finish();
                apply.accept(result);
                break;
            }
        }
        return true;
    }

    @Override public void onDestroyActionMode(ActionMode mode) {}
}
