package io.ticlext;

import Atom.File.FileUtility;
import Atom.Utility.Pool;
import me.tongfei.progressbar.ProgressBar;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static final Pattern emailRegex = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?");
    public static Map<String, Boolean> validDomainCache = Collections.synchronizedMap(new HashMap<>());
    
    public static void main(String[] args) throws IOException {
        File source = new File("./");
        File target = new File("./");
        BufferedReader brazil = new BufferedReader(new InputStreamReader(System.in));
        //Source Target Valid
        //Folder Folder V
        //Folder File if merged
        //File Folder V
        //File File V
        while (true) {
            System.out.print("Enter file path: ");
            String ans = brazil.readLine();
            if (!ans.isEmpty()){
                source = new File(ans);
            }
            if (!source.exists()){
                System.out.println("File not found");
            }else{
                if (source.isDirectory()){
                    System.out.println("Directory, searching all text files");
                }
                break;
            }
    
        }
        boolean mergeFiles = false;
        while (source.isDirectory()) {
            System.out.print("Merge files? (Y/N): ");
            String ans = brazil.readLine().toLowerCase();
            if (ans.equals("y")){
                mergeFiles = true;
                break;
            }else if (ans.equals("n")){
                mergeFiles = false;
                break;
            }else{
                System.out.println("Invalid input");
            }
        }
        while (true) {
            System.out.print("Enter Output path: ");
            String ans = brazil.readLine();
            if (!ans.isEmpty()){
                target = new File(ans);
            }
            if (target.exists()){
                if (target.isDirectory()){
                    if (source.isFile()){
                        target = new File(target, source.getName());
                    }
                    break;
                }else{
                    System.out.println("File already exists, try another file");
                }
            }else{
                if (source.isDirectory()){
                    if (!mergeFiles){
                        target.mkdirs();
                    }
                }
                break;
            }
        }
    
        ArrayList<File> files = new ArrayList<>();
        if (source.isDirectory()){
            Files.walk(source.toPath()).filter(Files::isRegularFile).forEach(f -> files.add(f.toFile()));
            if (files.size() == 0){
                System.out.println("No files found inside directory: " + source.getAbsolutePath());
                return;
            }
            System.out.println("Found " + files.size() + " files inside directory: " + source.getAbsolutePath());
        }else{
            files.add(source);
        }
        System.out.println("Writing to: '" + target.getAbsolutePath() + "' is a " + (target.isDirectory() ? " Directory" : "File"));
        while (true) {
            System.out.print("Confirm? (y/n): ");
            String confirm = brazil.readLine().toLowerCase();
            if (confirm.equals("y")){
                break;
            }else if (confirm.equals("n")){
                target.delete();
                return;
            }
        }
        List<Future<Report>> futures = new ArrayList<>();
        File finalOutput = target;
        boolean finalMergeFiles = mergeFiles;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".txt")){
    
    
                futures.add(Pool.async(() -> validateEmailList(file, finalOutput, finalMergeFiles)));
            }
        }
        ArrayList<Report> reports = new ArrayList<>();
        try(me.tongfei.progressbar.ProgressBar pb = new me.tongfei.progressbar.ProgressBar("Processing Files",
                futures.size())) {
            while (!futures.isEmpty()) {
                for (int i = 0; i < futures.size(); i++) {
                    Future<?> future = futures.get(i);
                    if (future.isDone()){
                        pb.step();
                        futures.remove(future);
                        try {
                            reports.add((Report) future.get());
                        }catch(InterruptedException | ExecutionException e){
                            e.printStackTrace();
                        }
                    }
                }
                Atom.Reflect.UnThread.sleep(250);
            }
    
        }
        System.out.println("Summary:");
        for (Report report : reports) {
            System.out.println(report.toString());
            System.out.println();
        }
    }
    
    public static boolean emailDomainValid(String domain) {
        if (validDomainCache.containsKey(domain)){
            return validDomainCache.get(domain);
        }
        try {
            InetAddress.getByName(domain);
            validDomainCache.put(domain, true);
            return true;
        }catch(UnknownHostException e){
            validDomainCache.put(domain, false);
            return false;
        }
    }
    
    public static String checkEmail(Report report, String line) {
        line = line.toLowerCase().trim();
        if (line.isEmpty()){
            return null;
        }
        for (char c : line.toCharArray()) {
            if (c == '@') report.possibleEmailsCount++;
        }
        Matcher matcher = emailRegex.matcher(line);
        if (matcher.find()){
            String email = matcher.group();
            if (!email.isEmpty()){
                String[] parts = email.split("@");
                if (parts.length == 2){
                    String domain = parts[1];
                    if (emailDomainValid(domain)){
                        return email;
                    }else{
                        report.invalidDomains++;
                    }
                }else{
                    report.invalidEmails++;
                }
            }
    
    
        }else{
            report.invalidEmails++;
        }
        return null;
    }
    
    public static Report validateEmailList(File srcTxt, File outputFile, boolean mergeFile) {
        Report report = new Report(srcTxt);
        long max = 0;
        try(ProgressBar pb = new ProgressBar(srcTxt.getName(), max)) {
            HashSet<String> emails = new HashSet<>();
            List<String> lines = new LinkedList<>();
            try(BufferedReader br = new BufferedReader(new FileReader(srcTxt))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                    report.totalLines++;
                    max++;
                    max++;
                    max++;
                    pb.maxHint(max);
                    pb.step();
                }
            }catch(IOException e){
                System.err.println("Error Reading File: " + e.getMessage());
                throw e;
                
            }
            for (String line : lines) {
                String email = checkEmail(report, line);
                pb.step();
                if (email == null) continue;
                if (emails.add(email)){
                    report.validEmails++;
                }else{
                    report.duplicateEmails++;
                }
    
            }
            File newFile;
            if (mergeFile){
                newFile = outputFile;
            }else{
                newFile = outputFile.isDirectory() ? new File(outputFile, srcTxt.getName()) : srcTxt;
            }
            FileUtility.makeFile(newFile);
            try(BufferedWriter bw = new BufferedWriter(new FileWriter(newFile, mergeFile))) {
                for (String line2 : emails) {
                    bw.write(line2);
                    bw.newLine();
                    pb.step();
                }
            }catch(IOException e){
                System.err.println("Error Writing File: " + e.getMessage());
                throw e;
            }
            
        }catch(Exception e){
            report.error = e;
        }
        return report;
    }
    
    public static class Report implements Serializable {
        public File file;
        public long totalLines;
        public long invalidDomains;
        public long invalidEmails;
        public long duplicateEmails;
        public long validEmails;
        public long possibleEmailsCount;
        public Throwable error;
        
        public Report(File file) {
            this.file = file;
        }
        
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Report for ").append(file.getPath()).append(System.lineSeparator());
            sb.append("Total Lines: ").append(totalLines).append(System.lineSeparator());
            sb.append("Invalid Domains: ").append(invalidDomains).append(System.lineSeparator());
            sb.append("Invalid Emails: ").append(invalidEmails).append(System.lineSeparator());
            sb.append("Duplicate Emails: ").append(duplicateEmails).append(System.lineSeparator());
            sb.append("Valid Emails: ").append(validEmails).append(System.lineSeparator());
            sb.append("Total Emails: ")
                    .append(validEmails + duplicateEmails + invalidEmails)
                    .append(System.lineSeparator());
            sb.append("Total Possible Emails (by counting @): ")
                    .append(possibleEmailsCount)
                    .append(System.lineSeparator());
            if (error != null){
                sb.append("Error: ")
                        .append(error.getMessage() == null ? error : error.getMessage())
                        .append(System.lineSeparator());
            }
            return sb.toString();
        }
    }
    
}
