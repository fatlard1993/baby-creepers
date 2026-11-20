package justfatlard.baby_creepers.gametest;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Models;
import justfatlard.pandorical.gametest.Pictures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * On a client with Pandorical a baby creeper is drawn with its baby model: drawn at full scale (the
 * model is small already, and the server's half is undone in the drawing), 14 pixels tall, with a
 * 6-pixel head. Against it, a grown creeper at half scale, which is what a vanilla client draws: about
 * as tall, but with a 4-pixel head. Measured off the models the game would draw, posed as it would
 * pose them, and photographed beside a grown creeper.
 */
public final class BabiesLookLikeBabies implements FabricClientGameTest {

	private static final double HEIGHT = 14 / 16.0;
	private static final double HEAD = 6 / 16.0;
	private static final double HALF_GROWN_HEAD = 4 / 16.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isContentReady(connection.getServerPlayer()));
			server.runCommand("difficulty normal");
			server.runCommand("gamemode creative @a");
			Pictures.stage(context, server, 6000);

			BlockPos base = server.computeOnServer(s -> Pictures.ground(s.overworld(),
				connection.getServerPlayer().getBlockX(), connection.getServerPlayer().getBlockZ()));

			int[] ids = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Creeper baby = place(level, base, 0.6, 0);
				BabyCreepers.become(baby);
				Creeper grown = place(level, base, -0.9, 0);
				Creeper halfGrown = place(level, base, 3, -6);
				halfGrown.getAttribute(Attributes.SCALE).addOrReplacePermanentModifier(new AttributeModifier(
					Identifier.fromNamespaceAndPath("baby-creepers-gametest", "half"), BabyCreepers.SCALE - 1F,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				return new int[] {baby.getId(), grown.getId(), halfGrown.getId()};
			});

			context.waitFor(client -> client.level.getEntity(ids[0]) != null && client.level.getEntity(ids[2]) != null
				&& Models.assigned(ids[0]) != null);
			context.waitTicks(5);

			String measured = context.computeOnClient(client -> {
				StringBuilder wrong = new StringBuilder();
				Measure baby = measure(client, ids[0]);
				Measure halfGrown = measure(client, ids[2]);
				System.out.println("[baby-creepers test] baby: " + baby);
				System.out.println("[baby-creepers test] half-scale grown creeper (vanilla clients): " + halfGrown);
				if (Math.abs(baby.scale - 1F) > 1.0E-4) wrong.append("baby drawn at scale ").append(baby.scale).append("; ");
				if (!near(baby.height, HEIGHT)) wrong.append("baby stands ").append(baby.height).append("; ");
				if (!near(baby.head, HEAD)) wrong.append("baby's head is ").append(baby.head).append("; ");
				if (!near(halfGrown.head, HALF_GROWN_HEAD)) wrong.append("a half-scale grown creeper's head is ").append(halfGrown.head).append(", so the check proves nothing; ");
				return wrong.toString();
			});
			check(measured.isEmpty(), measured);

			Pictures.look(server, Vec3.atBottomCenterOf(base).add(0, 0.6, 3.4), Vec3.atBottomCenterOf(base).add(0, 0.6, 0));
			System.out.println("[baby-creepers test] picture " + Pictures.shoot(context, connection, "baby-creeper-model").toAbsolutePath());
		}
		Kit.passed("BabiesLookLikeBabies");
	}

	private record Measure(String model, float scale, double height, double head) {
		@Override
		public String toString() {
			return "%s at scale %.3f, %.4f blocks tall, head %.4f".formatted(model, scale, height, head);
		}
	}

	/** As the game draws it: its renderer's model, posed for its state, with whatever Pandorical lays over the pose. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Measure measure(Minecraft client, int id) {
		Entity entity = client.level.getEntity(id);
		EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(entity);
		LivingEntityRenderState state = (LivingEntityRenderState) renderer.createRenderState(entity, 1F);
		EntityModel model = ((LivingEntityRenderer) renderer).getModel();
		Models.pose(model, state);
		return measure(model, state);
	}

	/** Placed as {@code LivingEntityRenderer.submit} places a model, in blocks: sole to crown, and the head's own height. */
	private static Measure measure(EntityModel<?> model, LivingEntityRenderState state) {
		PoseStack pose = new PoseStack();
		pose.scale(state.scale, state.scale, state.scale);
		pose.scale(-1F, -1F, 1F);
		pose.translate(0F, -1.501F, 0F);
		double[] all = {Double.MAX_VALUE, -Double.MAX_VALUE};
		double[] head = {Double.MAX_VALUE, -Double.MAX_VALUE};
		List<String> main = List.of("/head", "/body", "/right_hind_leg", "/left_hind_leg", "/right_front_leg", "/left_front_leg");
		model.root().visit(pose, (p, path, index, cube) -> {
			if (index != 0 || !main.contains(path)) return;
			for (var polygon : cube.polygons) {
				for (var vertex : polygon.vertices()) {
					float y = p.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new org.joml.Vector3f()).y();
					all[0] = Math.min(all[0], y);
					all[1] = Math.max(all[1], y);
					if (path.equals("/head")) {
						head[0] = Math.min(head[0], y);
						head[1] = Math.max(head[1], y);
					}
				}
			}
		});
		return new Measure(model.getClass().getSimpleName(), state.scale, all[1] - all[0], head[1] - head[0]);
	}

	/** Standing still, facing south toward the camera. */
	private static Creeper place(ServerLevel level, BlockPos base, double dx, double dz) {
		Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		creeper.snapTo(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz, 0F, 0F);
		creeper.setYHeadRot(0F);
		creeper.setYBodyRot(0F);
		creeper.setNoAi(true);
		creeper.setPersistenceRequired();
		level.addFreshEntity(creeper);
		return creeper;
	}

	/** Within a pixel's worth on a 14-pixel baby. */
	private static boolean near(double actual, double expected) {
		return Math.abs(actual - expected) <= 0.5 / 16.0;
	}
}
