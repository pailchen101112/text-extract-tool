package com.example.extracttool.dto;

public class NeedleResult {

    private String needle;
    private boolean found;
    private int count;
    private int firstIndex;

    public NeedleResult() {}

    public NeedleResult(String needle, boolean found, int count, int firstIndex) {
        this.needle = needle;
        this.found = found;
        this.count = count;
        this.firstIndex = firstIndex;
    }

    public String getNeedle() { return needle; }
    public void setNeedle(String needle) { this.needle = needle; }

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public int getFirstIndex() { return firstIndex; }
    public void setFirstIndex(int firstIndex) { this.firstIndex = firstIndex; }
}
