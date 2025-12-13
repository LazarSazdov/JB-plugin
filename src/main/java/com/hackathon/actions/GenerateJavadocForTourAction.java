package com.hackathon.actions;

import com.hackathon.model.TourStep;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateJavadocForTourAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        TourStateService state = project.getService(TourStateService.class);
        if (state.getSteps().isEmpty()) {
            Messages.showInfoMessage(project, "No tour loaded or steps present.", "Generate Javadoc");
            return;
        }

        int[] updated = {0};
        WriteCommandAction.runWriteCommandAction(project, "Generate Javadoc for Tour", null, () -> {
            for (TourStep step : state.getSteps()) {
                VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(step.filePath());
                if (vFile == null) continue;
                PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
                if (!(psiFile instanceof PsiJavaFile)) continue;

                int line0 = Math.max(0, step.lineNum() - 1);
                Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                if (document == null || line0 >= document.getLineCount()) continue;
                int offset = document.getLineStartOffset(line0);
                PsiElement el = psiFile.findElementAt(offset);
                PsiElement symbol = findEnclosing(el);
                if (!(symbol instanceof PsiDocCommentOwner owner)) continue;

                String acwBlock = buildAcwBlockFromHtml(step);
                if (acwBlock == null) continue;
                applyOrUpdateJavadoc(project, owner, acwBlock);
                updated[0]++;
            }
        });

        Messages.showInfoMessage(project, "Updated Javadoc for " + updated[0] + " element(s).", "Generate Javadoc");
    }

    private static PsiElement findEnclosing(PsiElement el) {
        while (el != null && !(el instanceof PsiMethod) && !(el instanceof PsiClass)) {
            el = el.getParent();
        }
        return el;
    }

    // Build only our ACW block with markers, preserve user tags elsewhere
    private static String buildAcwBlockFromHtml(TourStep step) {
        String html = step.aiExplanation();
        if (html == null || html.isBlank()) return null;
        String summary = html.replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)</?body>", "")
                .replaceAll("(?is)</?html>", "")
                .replaceAll("(?is)<h[12][^>]*>(.*?)</h[12]>", "$1: ");

        // Extract first <pre><code> ... </code></pre>
        Pattern p = Pattern.compile("(?is)<pre>\\s*<code[^>]*>(.*?)</code>\\s*</pre>");
        Matcher m = p.matcher(html);
        String code = null;
        if (m.find()) {
            code = m.group(1)
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&amp;", "&");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" * <ACW-BEGIN>\n");
        for (String line : escapeForJavadoc(summary).split("\r?\n")) {
            sb.append(" * ").append(line).append("\n");
        }
        if (code != null && !code.isBlank()) {
            sb.append(" * <p>Usage example:</p>\n");
            sb.append(" * <pre><code>\n");
            for (String line : code.split("\r?\n")) {
                sb.append(" * ").append(escapeForJavadoc(line)).append("\n");
            }
            sb.append(" * </code></pre>\n");
        }
        sb.append(" * <ACW-END>\n");
        return sb.toString();
    }

    private static String escapeForJavadoc(String s) {
        return s.replace("*/", "*\\/" );
    }

    private static void applyOrUpdateJavadoc(Project project, PsiDocCommentOwner owner, String acwBlock) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiDocComment existing = owner.getDocComment();

        StringBuilder docText = new StringBuilder();
        docText.append("/**\n");

        if (existing != null) {
            // Remove previous ACW block from existing description
            String[] lines = existing.getText().split("\r?\n");
            boolean inAcw = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("/**") || trimmed.equals("*/")) continue; // skip comment markers
                if (trimmed.contains("<ACW-BEGIN>")) { inAcw = true; continue; }
                if (trimmed.contains("<ACW-END>")) { inAcw = false; continue; }
                if (!inAcw && !trimmed.startsWith("*@")) {
                    // keep non-tag description lines other than ACW? We'll prefer our ACW block as description
                    // so skip old description to avoid duplication
                }
            }

            // Add our ACW block as the main description
            docText.append(acwBlock);

            // Preserve tags
            PsiDocTag[] tags = existing.getTags();
            if (tags.length > 0) {
                docText.append(" *\n");
                for (PsiDocTag tag : tags) {
                    String t = tag.getText();
                    for (String ln : t.split("\r?\n")) {
                        docText.append(" * ").append(ln.trim()).append("\n");
                    }
                }
            }
        } else {
            // No existing: just ACW block
            docText.append(acwBlock);
        }

        docText.append(" */");

        PsiDocComment newDoc = factory.createDocCommentFromText(docText.toString());
        if (existing != null) {
            existing.replace(newDoc);
        } else {
            owner.addBefore(newDoc, owner.getFirstChild());
        }
    }
}
