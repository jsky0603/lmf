package de.skyz.skyzgallery.client;

import de.skyz.skyzgallery.entity.EntityWallImage;
import de.skyz.skyzgallery.image.ImageReference;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** A thin, wall-mounted image with a dark frame; never accesses the server filesystem. */
public final class RenderWallImage extends Render<EntityWallImage> {

    public RenderWallImage(RenderManager manager) {
        super(manager);
    }

    @Override
    public void doRender(EntityWallImage image, double x, double y, double z,
            float entityYaw, float partialTicks) {
        ImageReference reference = image.getReference();
        if (reference == null || image.facingDirection == null) {
            return;
        }
        float halfW = reference.width * 0.5F;
        float halfH = reference.height * 0.5F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0F - image.rotationYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();

        colorQuad(-halfW, -halfH, halfW, halfH, 0.0F, 31, 23, 39);
        colorQuad(-halfW + 0.055F, -halfH + 0.055F,
                halfW - 0.055F, halfH - 0.055F, 0.002F, 246, 230, 246);

        ResourceLocation texture = ClientImageCache.INSTANCE.get(reference.hash);
        if (texture != null) {
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            bindTexture(texture);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.getBuffer();
            float left = -halfW + 0.065F;
            float right = halfW - 0.065F;
            float bottom = -halfH + 0.065F;
            float top = halfH - 0.065F;
            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            builder.pos(left, bottom, 0.004D).tex(0.0D, 1.0D).endVertex();
            builder.pos(right, bottom, 0.004D).tex(1.0D, 1.0D).endVertex();
            builder.pos(right, top, 0.004D).tex(1.0D, 0.0D).endVertex();
            builder.pos(left, top, 0.004D).tex(0.0D, 0.0D).endVertex();
            tessellator.draw();
        } else {
            GlStateManager.enableTexture2D();
        }

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        super.doRender(image, x, y, z, entityYaw, partialTicks);
    }

    private static void colorQuad(float left, float bottom, float right, float top,
            float depth, int red, int green, int blue) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        builder.pos(left, bottom, depth).color(red, green, blue, 255).endVertex();
        builder.pos(right, bottom, depth).color(red, green, blue, 255).endVertex();
        builder.pos(right, top, depth).color(red, green, blue, 255).endVertex();
        builder.pos(left, top, depth).color(red, green, blue, 255).endVertex();
        tessellator.draw();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityWallImage image) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
