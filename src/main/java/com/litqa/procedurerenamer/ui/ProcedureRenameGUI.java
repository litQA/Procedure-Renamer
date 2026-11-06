package com.litqa.procedurerenamer.ui;

import com.litqa.procedurerenamer.element.types.ProcedureRename;
import com.litqa.procedurerenamer.util.ConfigSync;
import com.litqa.procedurerenamer.util.NameRules;
import net.mcreator.element.ModElementType;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.validation.AggregatedValidationResult;
import net.mcreator.ui.validation.ValidationResult;
import net.mcreator.ui.validation.component.VTextField;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * GUI for the singleton Procedure Rename element.
 */
public class ProcedureRenameGUI extends ModElementGUI<ProcedureRename> {

    private final MCreator mcreatorRef;

    private final JPanel rowsPanel = new JPanel();
    private final List<Row> rows = new ArrayList<>();

    private boolean refreshing;

    public ProcedureRenameGUI(MCreator mcreator, ModElement modElement, boolean editingMode) {
        super(mcreator, modElement, editingMode);
        this.mcreatorRef = mcreator;
        initGUI();

        if (!editingMode && hasOtherInstance()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    mcreator,
                    "Another Procedure Rename element already exists in this workspace.\n"
                            + "Only one Procedure Rename element is allowed per workspace.",
                    "Procedure Renamer",
                    JOptionPane.WARNING_MESSAGE
            ));
        }
    }

    @Override
    protected void initGUI() {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new GridBagLayout());
        addHeader(header);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(rowsPanel);
        scrollPane.setPreferredSize(new Dimension(780, 380));
        scrollPane.setBorder(BorderFactory.createLineBorder(new java.awt.Color(190, 190, 190)));

        JButton addButton = new JButton("+ Add entry");
        addButton.addActionListener(event -> addEmptyRow());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.add(addButton);

        root.add(header, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        addPage("Procedure Rename", root);
        finalizeGUI();

        if (!isEditingMode()) {
            populateDefaults();
        }
    }

    private void addHeader(JPanel header) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 4, 0, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        constraints.gridx = 0;
        constraints.weightx = 0.35;
        header.add(new JLabel("Procedure"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 0.25;
        header.add(new JLabel("Target class"), constraints);

        constraints.gridx = 2;
        constraints.weightx = 0.35;
        header.add(new JLabel("Target package"), constraints);

        constraints.gridx = 3;
        constraints.weightx = 0.05;
        header.add(new JLabel(""), constraints);
    }

    @Override
    public void openInEditingMode(ProcedureRename element) {
        rows.clear();
        rowsPanel.removeAll();

        List<ProcedureRename.RenameEntry> entries = element.getEntries();
        if (!entries.isEmpty()) {
            for (ProcedureRename.RenameEntry entry : entries) {
                if (entry != null) {
                    addRow(entry.procedureName, entry.targetClassName, entry.targetPackage);
                }
            }
        } else if (!isEditingMode()) {
            populateDefaults();
        } else {
            addEmptyRow();
        }

        refreshProcedureChoices();
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private void populateDefaults() {
        if (isEditingMode()) {
            return;
        }

        rows.clear();
        rowsPanel.removeAll();

        List<String> procedureNames = getProcedureNames();
        if (procedureNames.isEmpty()) {
            addEmptyRow();
        } else {
            for (String procedureName : procedureNames) {
                addRow(procedureName, "", "");
            }
        }

        refreshProcedureChoices();
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private void addEmptyRow() {
        addRow("", "", "");
    }

    private void addRow(String procedureName, String targetClassName, String targetPackage) {
        Row row = new Row();
        row.pendingSelection = procedureName == null ? "" : procedureName;
        row.className.setText(targetClassName == null ? "" : targetClassName);
        row.packageName.setText(targetPackage == null ? "" : targetPackage);

        row.procedure.addActionListener(event -> {
            if (!refreshing) {
                refreshProcedureChoices();
            }
        });
        row.removeButton.addActionListener(event -> {
            rows.remove(row);
            rowsPanel.remove(row.panel);
            refreshProcedureChoices();
            rowsPanel.revalidate();
            rowsPanel.repaint();
        });

        rows.add(row);
        rowsPanel.add(row.panel);
        refreshProcedureChoices();
    }

    private void refreshProcedureChoices() {
        if (refreshing) {
            return;
        }

        refreshing = true;
        try {
            List<String> allProcedures = getProcedureNames();
            List<String> effectiveSelections = new ArrayList<>();

            for (Row row : rows) {
                String selection = getSelectedProcedure(row);
                if (selection.isEmpty() && row.pendingSelection != null) {
                    selection = row.pendingSelection;
                }
                effectiveSelections.add(selection);
            }

            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                Set<String> selectedByOthers = new LinkedHashSet<>();
                for (int otherIndex = 0; otherIndex < rows.size(); otherIndex++) {
                    if (otherIndex != index && !effectiveSelections.get(otherIndex).isEmpty()) {
                        selectedByOthers.add(effectiveSelections.get(otherIndex));
                    }
                }

                List<String> available = new ArrayList<>();
                for (String procedureName : allProcedures) {
                    if (!selectedByOthers.contains(procedureName)) {
                        available.add(procedureName);
                    }
                }

                String ownSelection = effectiveSelections.get(index);
                if (!ownSelection.isEmpty() && !available.contains(ownSelection)) {
                    available.add(ownSelection);
                }
                Collections.sort(available);

                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                model.addElement("");
                for (String procedureName : available) {
                    model.addElement(procedureName);
                }
                row.procedure.setModel(model);

                if (!ownSelection.isEmpty() && available.contains(ownSelection)) {
                    row.procedure.setSelectedItem(ownSelection);
                } else {
                    row.procedure.setSelectedItem("");
                }
                row.pendingSelection = null;
            }
        } finally {
            refreshing = false;
        }
    }

    private List<String> getProcedureNames() {
        Set<String> names = new TreeSet<>();
        Workspace workspace = mcreatorRef.getWorkspace();
        if (workspace != null) {
            for (ModElement element : workspace.getModElements()) {
                ModElementType<?> type = element.getType();
                if (type != null && "procedure".equals(type.getRegistryName())) {
                    names.add(element.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private String getSelectedProcedure(Row row) {
        Object selected = row.procedure.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    @Override
    public ProcedureRename getElementFromGUI() {
        ProcedureRename element = new ProcedureRename(modElement);
        List<ProcedureRename.RenameEntry> entries = new ArrayList<>();

        for (Row row : rows) {
            String procedureName = getSelectedProcedure(row);
            if (procedureName.isEmpty()) {
                continue;
            }
            ProcedureRename.RenameEntry entry = new ProcedureRename.RenameEntry(procedureName);
            entry.targetClassName = row.className.getText().trim();
            entry.targetPackage = row.packageName.getText().trim();
            entries.add(entry);
        }

        element.entries = entries;
        return element;
    }

    @Override
    protected AggregatedValidationResult getAdditionalValidationResult(ProcedureRename element) {
        if (hasOtherInstance()) {
            return new AggregatedValidationResult.FAIL(
                    "Another Procedure Rename element already exists in this workspace. "
                            + "Only one Procedure Rename element is allowed.");
        }
        return super.getAdditionalValidationResult(element);
    }

    private boolean hasOtherInstance() {
        Workspace workspace = mcreatorRef.getWorkspace();
        if (workspace == null) {
            return false;
        }

        for (ModElement element : workspace.getModElements()) {
            if (element == modElement || element.equals(modElement)) {
                continue;
            }
            ModElementType<?> type = element.getType();
            if (type != null && "procedure_rename".equals(type.getRegistryName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void afterGeneratableElementStored() {
        super.afterGeneratableElementStored();
        ConfigSync.sync(modElement.getWorkspace());
    }

    @Override
    public void reloadDataLists() {
        super.reloadDataLists();
        refreshProcedureChoices();
    }

    @Override
    public URI contextURL() throws URISyntaxException {
        return new URI("https://mcreator.net/");
    }

    private ValidationResult validateClassField(String classNameValue, String packageValue) {
        String className = classNameValue == null ? "" : classNameValue.trim();
        if (className.isEmpty()) {
            return ValidationResult.PASSED;
        }

        String packageName = packageValue == null ? "" : packageValue.trim();
        boolean validPackage = !packageName.isEmpty() && NameRules.isValidPackage(packageName);
        if (!NameRules.isValidClassName(className)) {
            return new ValidationResult(ValidationResult.Type.WARNING,
                    validPackage
                            ? "Invalid class name. The generated class name will be kept and the valid package will still be applied."
                            : "Invalid class name. No change will be made unless a valid target package is provided.");
        }

        if (!validPackage) {
            return new ValidationResult(ValidationResult.Type.WARNING,
                    "A valid target package is required to change the class name. This entry will make no change.");
        }

        return ValidationResult.PASSED;
    }

    private ValidationResult validatePackageField(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || NameRules.isValidPackage(text)) {
            return ValidationResult.PASSED;
        }
        return new ValidationResult(ValidationResult.Type.WARNING,
                "Invalid package. This entry will make no change.");
    }

    private final class Row {

        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JComboBox<String> procedure = new JComboBox<>();
        private final VTextField className = new VTextField(18);
        private final VTextField packageName = new VTextField(24);
        private final JButton removeButton = new JButton("x");

        private String pendingSelection = "";

        private Row() {
            procedure.setPreferredSize(new Dimension(230, 28));
            procedure.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    String text = value == null ? "" : value.toString();
                    if (text.isEmpty()) {
                        text = "- select procedure -";
                    }
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                }
            });

            className.setToolTipText("Target Java class name; leave empty to keep the generated name. "
                    + "A valid target package is required to change the class name.");
            packageName.setToolTipText("Target Java package; required for any rename. Leave empty to skip.");
            className.setValidator(() -> validateClassField(className.getText(), packageName.getText()));
            packageName.setValidator(() -> validatePackageField(packageName.getText()));
            className.enableRealtimeValidation();
            packageName.enableRealtimeValidation();

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(2, 4, 2, 4);
            constraints.fill = GridBagConstraints.HORIZONTAL;

            constraints.gridx = 0;
            constraints.weightx = 0.35;
            panel.add(procedure, constraints);

            constraints.gridx = 1;
            constraints.weightx = 0.25;
            panel.add(className, constraints);

            constraints.gridx = 2;
            constraints.weightx = 0.35;
            panel.add(packageName, constraints);

            constraints.gridx = 3;
            constraints.weightx = 0.05;
            constraints.fill = GridBagConstraints.NONE;
            panel.add(removeButton, constraints);

            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        }
    }
}
