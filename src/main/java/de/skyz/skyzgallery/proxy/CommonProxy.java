package de.skyz.skyzgallery.proxy;

import de.skyz.skyzgallery.network.ImageNetwork;

/** Never reference client rendering classes from common/server code. */
public class CommonProxy {

    public void preInit() {
    }

    public void receiveChunk(ImageNetwork.Chunk packet) {
    }
}
