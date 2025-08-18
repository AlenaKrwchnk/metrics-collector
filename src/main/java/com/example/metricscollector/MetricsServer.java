package com.example.metricscollector;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class MetricsServer {
    private final Config cfg;
    private final MetricStore store;

    public MetricsServer(Config cfg, MetricStore store) {
        this.cfg = cfg; this.store = store;
    }

    public void start() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup(cfg.workerThreads());

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new MetricDecoder(), new MetricHandler(store));
                        }
                    });
            ChannelFuture f = b.bind(cfg.port()).sync();
            System.out.println("Server started on port " + cfg.port());
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }

    static class MetricDecoder extends ByteToMessageDecoder {
        @Override protected void decode(ChannelHandlerContext ctx, io.netty.buffer.ByteBuf in, List<Object> out) {
            in.markReaderIndex();
            if (in.readableBytes() < 8+1+8) { in.resetReaderIndex(); return; }
            long ts = in.readLong();
            int nameLen = in.readUnsignedByte();
            if (in.readableBytes() < nameLen+8) { in.resetReaderIndex(); return; }
            byte[] nameBytes = new byte[nameLen];
            in.readBytes(nameBytes);
            double val = in.readDouble();
            out.add(new MetricPoint(new String(nameBytes, StandardCharsets.UTF_8), Instant.ofEpochMilli(ts), val));
        }
    }

    static class MetricHandler extends SimpleChannelInboundHandler<MetricPoint> {
        private final MetricStore store;
        public MetricHandler(MetricStore store) { this.store = store; }
        @Override protected void channelRead0(ChannelHandlerContext ctx, MetricPoint msg) { store.add(msg); }
    }
}
