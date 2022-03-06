package io.ticlext;

import Atom.File.FileUtility;
import Atom.Reflect.UnThread;
import Atom.Time.Timer;
import Atom.Utility.Pool;
import io.ticlext.hotel.HotelContinentScrapper;
import io.ticlext.hotel.HotelRegionPage;
import io.ticlext.restaurant.RestaurantContinentScrapper;
import io.ticlext.restaurant.RestaurantRegionPage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Main {
    public static final Pattern emailRegex = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?");
    static final Properties headers = new Properties();
    static final Map<String, PrintStream> writers = Collections.synchronizedMap(new HashMap<>());
    public static String baseURL = "https://www.tripadvisor.com";
    public static SortBy sortBy = SortBy.COUNTRY;
    static URL restaurantsURL = safeURL("https://www.tripadvisor.com/Restaurants-g4-Europe.html"), hotelsURL = safeURL(
            "https://www.tripadvisor.com/Hotels-g4-Europe-Hotels.html");
    static File saveFile = new File("ScrapDataCheckpoint.json");
    static File headerFile = new File("header.properties");
    static File dataFolder;
    static Timer saveTime = new Timer(TimeUnit.SECONDS, 10);
    static volatile ThreadLocal<Proxy> proxySupplier;
    static ThreadLocal<Properties> headersSupplier;
    
    static {
        headers.setProperty("User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.80 Safari/537.36");
        headers.setProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        headers.setProperty("Accept-Language", "en-US,en;q=0.9");
        headers.setProperty("Upgrade-Insecure-Requests", "1");
        headersSupplier = ThreadLocal.withInitial(() -> {
            Properties p = new Properties();
            p.putAll(headers);
            return p;
        });
    }
    
    public static URL safeURL(String url) {
        try {
            return new URL(url);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    public static void setDataFolder(String dataFolder) {
        Main.dataFolder = new File(dataFolder);
        if (!Main.dataFolder.exists()) Main.dataFolder.mkdirs();
    }
    
    public static void saveHeader(File headerFile) {
        try(FileOutputStream fos = new FileOutputStream(headerFile)) {
            headers.store(fos, "Header");
        }catch(IOException e){
            System.err.println("Error saving header file");
        }
    }
    
    public static void readHeader(File headerFile) {
        
        try(FileInputStream fis = new FileInputStream(headerFile)) {
            headers.clear();
            headers.load(fis);
        }catch(IOException e){
            System.err.println("Error reading header file");
        }
        headers.remove("accept-encoding");
    }
    
    public static void write(String country, String email) {
        if (email == null) return;
        if (email.isEmpty()) return;
        if (!emailRegex.matcher(email).matches()) return;
        if (!writers.containsKey(country)){
            try {
                writers.put(country,
                        new PrintStream(new FileOutputStream(new File(dataFolder, country + ".txt")), true));
            }catch(FileNotFoundException e){
                System.err.println("Error opening file: " + country + ".txt");
                return;
            }
        }
        writers.get(country).println(email);
    }
    
    public static List<File> closeWriters() {
        writers.values().forEach(PrintStream::close);
        ArrayList<File> files = new ArrayList<>();
        for (String country : writers.keySet()) {
            File f = new File(dataFolder, country + ".txt");
            files.add(f);
            
        }
        writers.clear();
        return files;
    }
    
    public static void main(String[] args) throws Throwable {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        boolean first = true;
        if (saveFile.exists()){
            while (true) {
                System.out.print("Checkpoint (" + saveFile + ") found, continue from there? (y/n)");
                String ans = br.readLine().toLowerCase();
                if (ans.equals("y")){
                    first = false;
                    break;
                }else if (ans.equals("n")){
                    break;
                }else{
                    System.out.println("Invalid input");
                }
            }
        }
        while (first) {
            try {
                System.out.print("Enter Restaurant URL to scrap [" + restaurantsURL + "]: ");
                String url = br.readLine();
                if (url.length() > 0){
                    restaurantsURL = new URL(url);
                }
                break;
            }catch(MalformedURLException e){
                System.err.println("Invalid URL");
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        while (first) {
            try {
                System.out.print("Enter Hotels URL to scrap [" + restaurantsURL + "]: ");
                String url = br.readLine();
                if (url.length() > 0){
                    hotelsURL = new URL(url);
                }
                break;
            }catch(MalformedURLException e){
                System.err.println("Invalid URL");
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        
        while (first) {
            try {
                //System.out.print("Enter Data Folder: [data/]: ");
                //String url = br.readLine();
                String url = "";
                if (false){
                    throw new IOException("Invalid folder");
                }
                if (url.length() > 0){
                    setDataFolder(url);
                }else{
                    setDataFolder("data/");
                }
                break;
            }catch(MalformedURLException e){
                System.err.println("Invalid URL");
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
        }
        while (true) {
            System.out.print("Use proxy? (y/n): ");
            String ans = br.readLine().toLowerCase();
            if (ans.equals("y")){
                proxySupplier = ThreadLocal.withInitial(new FreeProxyListNet());
                Pool.daemon(() -> {
                    while (!Thread.interrupted()) {
                        try {
                            FreeProxyListNet proxy = new FreeProxyListNet(true);
                            proxySupplier = ThreadLocal.withInitial(proxy);
                            UnThread.sleep(1000 * 60);
                        }catch(Exception e){
                            System.err.println("Error Changing Proxy: " + e.getMessage());
                        }
                    }
                });
                break;
            }else if (ans.equals("n")){
                break;
            }else{
                System.out.println("Invalid input");
            }
        }
    
    /*
        while (first) {
            try {
                System.out.println("Sort By: " + Arrays.toString(SortBy.values()));
                System.out.print("Sort by [" + sortBy + "]: ");
                String val = br.readLine();
                if (val.length() > 0){
                    sortBy = SortBy.valueOf(val);
                }
                break;
            }catch(MalformedURLException e){
                System.err.println("Invalid URL");
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
        }
        
     */
        ArrayList<String> argsList = new ArrayList<String>(Arrays.asList(args));
        
        if (headerFile.exists()){
            readHeader(headerFile);
        }
        float getAvailableRAMInGB = (float) Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024);
        getAvailableRAMInGB = getAvailableRAMInGB * 0.8f;
        System.out.println("Available RAM: " + getAvailableRAMInGB + " GB");
        //set thread
        int thread = Math.max((Runtime.getRuntime().availableProcessors()), 1);
        if (argsList.contains("--thread")){
            try {
                thread = Integer.parseInt(argsList.get(argsList.indexOf("--thread") + 1));
            }catch(NumberFormatException e){
                System.err.println("Invalid thread number");
                return;
            }
        }
        int finalThread = thread;
        Pool.parallelSupplier = () -> Executors.newFixedThreadPool(finalThread, (r) -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(t.getName() + "-Atomic-Executor");
            t.setDaemon(true);
            return t;
        });
        Pool.parallelAsync = Pool.parallelSupplier.get();
        System.out.println("Using " + thread + " threads");
        //set writers
        
        
        Thread hotelScrapper = null, restaurantScrapper = null;
        File hotelFile = new File(hotelsURL.getFile()), restaurantFile = new File(restaurantsURL.getFile());
        if (first && !hotelFile.exists()){
            System.out.println("Scrapping hotels: " + hotelsURL);
            HotelRegionPage.desc();
            HotelContinentScrapper regionScrapper = new HotelContinentScrapper(hotelsURL, hotel -> {
                String country = hotel.country;
                String email = hotel.email;
                write(country, email);
                
            });
            hotelScrapper = regionScrapper.init();
            hotelScrapper.join();
            FileUtility.write(hotelFile, "Done".getBytes(StandardCharsets.UTF_8));
        }
        if (first && !restaurantFile.exists()){
            System.out.println("Scrapping restaurants: " + restaurantsURL);
            RestaurantRegionPage.desc();
            RestaurantContinentScrapper regionScrapper = new RestaurantContinentScrapper(restaurantsURL, restaurant -> {
                String country = restaurant.country;
                String email = restaurant.email;
                write(country, email);
            });
            
            restaurantScrapper = regionScrapper.init();
            restaurantScrapper.join();
            FileUtility.write(restaurantFile, "Done".getBytes(StandardCharsets.UTF_8));
        }
        reformatTextFile(closeWriters());
    }
    
    public static void reformatTextFile(List<File> files) {
        System.out.println("Reformatting text files");
        files.parallelStream().forEach(file -> {
            HashSet<String> lines = new HashSet<>();
            try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
                return;
            }
            try(BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (String line2 : lines) {
                    bw.write(line2);
                    bw.newLine();
                }
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
            
        });
    }
    
    public static void handleException(Throwable e) {
        e.printStackTrace();
    }
    
    
    public static String getHTTP(URL url) throws IOException {
        return getHTTP(url, false);
    }
    
    public static String getHTTP(URL url, boolean noCookie) throws IOException {
        boolean useProxy = proxySupplier != null;
        HttpURLConnection con = (HttpURLConnection) (useProxy ? url.openConnection(proxySupplier.get()) : url.openConnection());
        Properties head = headers;
        if (useProxy){
            head = headersSupplier.get();
        }
        for (Object o : head.keySet()) {
            String key = String.valueOf(o);
            if (noCookie){
                if (key.equals("Cookie")){
                    continue;
                }
            }
            con.setRequestProperty(key, head.getProperty(key));
        }
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        con.setDoInput(true);
        con.connect();
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        if (!noCookie){
            if (!head.containsKey("Cookie")){
                head.setProperty("Cookie", con.getHeaderField("Set-Cookie"));
                saveHeader(headerFile);
            }
        }
        return sb.toString();
    }
    
    public enum SortBy {
        CONTINENT, COUNTRY, REGION, CITY
    }
    
    
}
