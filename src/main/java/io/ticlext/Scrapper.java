package io.ticlext;

import Atom.Reflect.UnThread;
import Atom.Utility.Pool;
import me.tongfei.progressbar.ProgressBar;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class Scrapper<T> implements Serializable, Runnable {
    protected transient final List<Future<T>> futures = new ArrayList<>();
    protected transient ExecutorService executor;
    protected transient Future<String> htmlCache;
    protected URL nextURL;
    
    public void setExecutor(ExecutorService executor) {
        if (this.executor != null){
            throw new IllegalStateException("Executor already set");
        }
        this.executor = executor;
    }
    
    
    protected void setNextURL(URL nextURL) {
        this.nextURL = nextURL;
        if (nextURL != null){
            htmlCache = Pool.submit(() -> Main.getHTTP(nextURL));
        }
    }
    
    public String getHTMLNext() throws IOException {
        if (nextURL == null){
            throw new IllegalStateException("URL not set");
        }
        if (htmlCache != null && htmlCache.isDone()){
            try {
                return htmlCache.get();
            }catch(InterruptedException | ExecutionException ignored){}
        }
        
        return Main.getHTTP(nextURL);
    }
    
    //true if this scrapper employ another scrapper in a nested thread
    protected boolean nestedThreading() {
        return true;
    }
    
    //called when a future is done
    protected void onFuture(T t) {
    
    }
    
    protected void waitForNextPage(ProgressBar pb) {
        while (htmlCache != null && !htmlCache.isDone()) {
            processFuture(pb, false);
            UnThread.sleep(250);
        }
    }
    
    //process done futures
    protected void processFuture(ProgressBar pb, boolean wait) {
        while (futures.size() > 0) {
            pb.maxHint(pb.getCurrent() + futures.size());
            for (Future<T> f : new ArrayList<>(futures)) {
                if (f.isDone()){
                    try {
                        T t = f.get();
                        onFuture(t);
            
                    }catch(InterruptedException | ExecutionException ignored){
                    }
                    futures.remove(f);
                    pb.step();
                }
            }
            UnThread.sleep(250);
            if (!wait) break;
        }
    }
    
    protected void process(Callable<T> init) {
        Future<T> f = executor.submit(init);
        futures.add(f);
    }
    
    public Thread init() {
        if (executor == null){
            if (nestedThreading()) executor = Pool.parallelAsync;
            else executor = Pool.service;
        }
        if (nextURL == null){
            throw new IllegalStateException("URL not set");
        }
        if (nestedThreading()){
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
            return t;
        }else{
            run();
        }
        return null;
    }
    
    @Override
    protected void finalize() throws Throwable {
        if (futures.size() > 0){
            System.err.println(this.getClass().getSimpleName() + ": Futures not processed: " + futures.size());
        }
        super.finalize();
    }
}
