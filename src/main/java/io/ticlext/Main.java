package io.ticlext;

import Atom.File.FileUtility;
import Atom.Utility.Pool;
import me.tongfei.progressbar.ProgressBar;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
        //Folder File X
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
        while (true) {
            System.out.print("Enter Output path: ");
            String ans = brazil.readLine();
            if (!ans.isEmpty()){
                target = new File(ans);
            }
            if (target.exists()){
                if (target.isDirectory()){
                    break;
                }else{
                    System.out.println("File already exists, try another file");
                }
            }else{
                if (source.isDirectory()){
                    target.mkdirs();
                }
                break;
            }
        }
        if (source.isDirectory() && target.isFile()){
            throw new IllegalArgumentException("Source is a directory, Target is a file");
        }
        if (target.isDirectory() && !target.exists()){
            throw new IllegalArgumentException("Can't make target directory");
        }
        File[] files;
        if (source.isDirectory()){
            files = source.listFiles();
            if (files == null){
                System.out.println("No files found inside directory: " + source.getAbsolutePath());
                return;
            }
            System.out.println("Found " + files.length + " files inside directory: " + source.getAbsolutePath());
        }else{
            files = new File[]{source};
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
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".txt")){
                
                futures.add(Pool.async(() -> validateEmailList(file, finalOutput)));
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
            }
            Atom.Reflect.UnThread.sleep(250);
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
    
    public static Report validateEmailList(File file, File outputDir) {
        Report report = new Report(file);
        long max = 0;
        try(ProgressBar pb = new ProgressBar(file.getName(), max)) {
            HashSet<String> lines = new HashSet<>();
            try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.toLowerCase();
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
                                    if (lines.add(email)){
                                        report.validEmails++;
                                    }else{
                                        report.duplicateEmails++;
                                    }
                                }else{
                                    report.invalidDomains++;
                                }
                            }else{
                                report.invalidEmails++;
                            }
                        }
                        
                        
                    }else if (!line.isEmpty()){
                        report.invalidEmails++;
                    }
                    report.totalLines++;
                    max++;
                    max++;
                    pb.step();
                }
            }catch(IOException e){
                System.err.println("Error Reading File: " + e.getMessage());
                throw e;
                
            }
            File newFile = outputDir.isDirectory() ? new File(outputDir, file.getName()) : outputDir;
            FileUtility.makeFile(newFile);
            try(BufferedWriter bw = new BufferedWriter(new FileWriter(newFile))) {
                for (String line2 : lines) {
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
            sb.append("Report for ").append(file.getName()).append(System.lineSeparator());
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
                sb.append("Error: ").append(error.getMessage()).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }
    
}
