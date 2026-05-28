package com.mkac.meikomms.ui.workorder.model;

import java.util.ArrayList;
import java.util.List;

public class MaintenanceItem {
    public String checkId;
    public String parentCheckId;
    public String checkName;
    public String subDesc;
    public String min;
    public String max;
    public String checkValue;
    public String checkValue2;
    public String comment;
    public String historyJson;
    public int childCount;
    public String initialStatus;
    public String imagePath;
    public final List<String> imagePaths = new ArrayList<>();
    
    public String originalCheckValue;
    public String originalCheckValue2;
    public String originalComment;
    public List<String> originalImagePaths = new ArrayList<>();
    
    public boolean changed;
    public boolean locked;

    public boolean isNumericInput() {
        return (min != null && !min.trim().isEmpty()) || (max != null && !max.trim().isEmpty());
    }

    public boolean isRadioInput() {
        return !isNumericInput();
    }

    public void snapshotOriginalValues() {
        originalCheckValue = checkValue;
        originalCheckValue2 = checkValue2;
        originalComment = comment;
        originalImagePaths = new ArrayList<>(imagePaths);
        changed = false;
    }

    public void setImagePaths(List<String> paths) {
        imagePaths.clear();
        if (paths != null) {
            for (String path : paths) {
                addImagePathInternal(path);
            }
        }
        syncPrimaryImagePath();
    }

    public void addImagePath(String path) {
        addImagePathInternal(path);
        syncPrimaryImagePath();
    }

    public List<String> getImagePathsSnapshot() {
        ensureImagePaths();
        return new ArrayList<>(imagePaths);
    }

    public boolean hasImages() {
        ensureImagePaths();
        return !imagePaths.isEmpty();
    }

    public void ensureImagePaths() {
        if (imagePaths.isEmpty() && imagePath != null && !imagePath.trim().isEmpty()) {
            imagePaths.add(imagePath.trim());
        }
        syncPrimaryImagePath();
    }

    private void addImagePathInternal(String path) {
        if (path == null) return;
        String normalized = path.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) return;
        if (!imagePaths.contains(normalized)) {
            imagePaths.add(normalized);
        }
    }

    private void syncPrimaryImagePath() {
        imagePath = imagePaths.isEmpty() ? "" : imagePaths.get(0);
    }

    public void refreshChangedState() {
        changed = !safeEquals(originalCheckValue, checkValue) 
                || !safeEquals(originalCheckValue2, checkValue2)
                || !safeEquals(originalComment, comment)
                || !sameImagePaths(originalImagePaths, imagePaths);
    }

    public boolean isLockedOk() {
        return locked;
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) left = "";
        if (right == null) right = "";
        return left.trim().equals(right.trim());
    }

    private boolean sameImagePaths(List<String> leftPaths, List<String> rightPaths) {
        List<String> left = normalizeImagePathList(leftPaths);
        List<String> right = normalizeImagePathList(rightPaths);
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equals(right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<String> normalizeImagePathList(List<String> paths) {
        List<String> normalized = new ArrayList<>();
        if (paths == null) {
            return normalized;
        }
        for (String path : paths) {
            String value = path == null ? "" : path.trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }
}
