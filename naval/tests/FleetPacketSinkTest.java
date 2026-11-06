package com.empirecraft.fleettest;

import io.netty.buffer.Unpooled;
import java.util.concurrent.*;

/** Real Netty writes from concurrent server/physics callers, without a client. */
public final class FleetPacketSinkTest {
    public static void main(String[] args)throws Exception {
        var channel=FleetSmokeMod.packetSink();var pool=Executors.newFixedThreadPool(4);
        var start=new CountDownLatch(1);var tasks=new java.util.ArrayList<Future<?>>();
        try {
            for(int thread=0;thread<4;thread++)tasks.add(pool.submit(()->{
                start.await();for(int i=0;i<25000;i++){
                    var packet=Unpooled.buffer(1).writeByte(1);var sent=channel.writeAndFlush(packet);
                    if(!sent.isSuccess()||packet.refCnt()!=0)throw new AssertionError("Packet not consumed");
                }return null;
            }));
            start.countDown();for(var task:tasks)task.get(20,TimeUnit.SECONDS);
            if(channel.readOutbound()!=null)throw new AssertionError("Packet retained");
            System.out.println("FLEET_PACKET_SINK_PASS concurrent_packets=100000 writers=4");
        } finally {pool.shutdownNow();channel.finishAndReleaseAll();}
    }
}
