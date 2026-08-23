package com.mine.geometry_node.client.terminal.pty.pty4j;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Owns all Windows descendants and kills the job when the terminal tab is disposed. */
final class WindowsJobObject implements AutoCloseable {
    private static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
    private static final int JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
    private static final int PROCESS_ASSIGN_ACCESS = WinNT.PROCESS_SET_QUOTA | WinNT.PROCESS_TERMINATE;

    private final WinNT.HANDLE handle;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean assigned = new AtomicBoolean();
    private final AtomicReference<IOException> assignmentFailure = new AtomicReference<>();

    private WindowsJobObject(WinNT.HANDLE handle) {
        this.handle = handle;
    }

    static WindowsJobObject create() throws IOException {
        WinNT.HANDLE handle = JobKernel32.INSTANCE.CreateJobObject(null, null);
        if (handle == null) throw lastError("CreateJobObject");
        JobObjectExtendedLimitInformation information = new JobObjectExtendedLimitInformation();
        information.BasicLimitInformation.LimitFlags = new WinDef.DWORD(JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE);
        information.write();
        if (!JobKernel32.INSTANCE.SetInformationJobObject(handle, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION,
                information.getPointer(), information.size())) {
            JobKernel32.INSTANCE.CloseHandle(handle);
            throw lastError("SetInformationJobObject");
        }
        return new WindowsJobObject(handle);
    }

    void assign(long processId) {
        WinNT.HANDLE process = JobKernel32.INSTANCE.OpenProcess(PROCESS_ASSIGN_ACCESS, false, (int) processId);
        if (process == null) {
            assignmentFailure.compareAndSet(null, lastError("OpenProcess"));
            return;
        }
        try {
            if (!JobKernel32.INSTANCE.AssignProcessToJobObject(handle, process)) {
                assignmentFailure.compareAndSet(null, lastError("AssignProcessToJobObject"));
            } else {
                assigned.set(true);
            }
        } finally {
            JobKernel32.INSTANCE.CloseHandle(process);
        }
    }

    void requireAssigned() throws IOException {
        IOException failure = assignmentFailure.get();
        if (failure != null) throw failure;
        if (!assigned.get()) throw new IOException("PowerShell process was not assigned to its Windows Job Object");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { JobKernel32.INSTANCE.TerminateJobObject(handle, 1); } catch (RuntimeException ignored) { }
        try { JobKernel32.INSTANCE.CloseHandle(handle); } catch (RuntimeException ignored) { }
    }

    private static IOException lastError(String operation) {
        return new IOException(operation + " failed with Windows error " + Native.getLastError());
    }

    private interface JobKernel32 extends StdCallLibrary {
        JobKernel32 INSTANCE = Native.load("kernel32", JobKernel32.class, W32APIOptions.DEFAULT_OPTIONS);
        WinNT.HANDLE CreateJobObject(WinBase.SECURITY_ATTRIBUTES attributes, String name);
        boolean SetInformationJobObject(WinNT.HANDLE job, int informationClass,
                                        com.sun.jna.Pointer information, int informationLength);
        boolean AssignProcessToJobObject(WinNT.HANDLE job, WinNT.HANDLE process);
        boolean TerminateJobObject(WinNT.HANDLE job, int exitCode);
        WinNT.HANDLE OpenProcess(int desiredAccess, boolean inheritHandle, int processId);
        boolean CloseHandle(WinNT.HANDLE handle);
    }

    @Structure.FieldOrder({"PerProcessUserTimeLimit", "PerJobUserTimeLimit", "LimitFlags",
            "MinimumWorkingSetSize", "MaximumWorkingSetSize", "ActiveProcessLimit", "Affinity",
            "PriorityClass", "SchedulingClass"})
    public static final class JobObjectBasicLimitInformation extends Structure {
        public WinNT.LARGE_INTEGER PerProcessUserTimeLimit = new WinNT.LARGE_INTEGER();
        public WinNT.LARGE_INTEGER PerJobUserTimeLimit = new WinNT.LARGE_INTEGER();
        public WinDef.DWORD LimitFlags = new WinDef.DWORD();
        public BaseTSD.SIZE_T MinimumWorkingSetSize = new BaseTSD.SIZE_T();
        public BaseTSD.SIZE_T MaximumWorkingSetSize = new BaseTSD.SIZE_T();
        public WinDef.DWORD ActiveProcessLimit = new WinDef.DWORD();
        public BaseTSD.ULONG_PTR Affinity = new BaseTSD.ULONG_PTR();
        public WinDef.DWORD PriorityClass = new WinDef.DWORD();
        public WinDef.DWORD SchedulingClass = new WinDef.DWORD();
    }

    @Structure.FieldOrder({"BasicLimitInformation", "IoInfo", "ProcessMemoryLimit", "JobMemoryLimit",
            "PeakProcessMemoryUsed", "PeakJobMemoryUsed"})
    public static final class JobObjectExtendedLimitInformation extends Structure {
        public JobObjectBasicLimitInformation BasicLimitInformation = new JobObjectBasicLimitInformation();
        public WinNT.IO_COUNTERS IoInfo = new WinNT.IO_COUNTERS();
        public BaseTSD.SIZE_T ProcessMemoryLimit = new BaseTSD.SIZE_T();
        public BaseTSD.SIZE_T JobMemoryLimit = new BaseTSD.SIZE_T();
        public BaseTSD.SIZE_T PeakProcessMemoryUsed = new BaseTSD.SIZE_T();
        public BaseTSD.SIZE_T PeakJobMemoryUsed = new BaseTSD.SIZE_T();
    }
}
